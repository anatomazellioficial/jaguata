package com.jaguata.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Aceite de Termos, notificações por e-mail, edição/exclusão de vaga e candidato, catálogo de skills. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class RecursosTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";
    static final String CEP = "13015904";   // Campinas

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;
    @Autowired JdbcTemplate jdbc;

    record Conta(String id, String email, String token) {
    }

    ResultActions call(HttpMethod m, String url, String body, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (body != null && url.startsWith("/api/auth/registro/")) body = body.replaceFirst("\\{", "{\"aceiteTermos\":true,");
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    JsonNode corpo(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    String login(String email, String senha) throws Exception {
        return corpo(call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha), null)
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    String email() {
        return "r" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    void verificar(String email) throws Exception {
        call(HttpMethod.POST, "/api/auth/email/verificar",
                "{\"token\":\"" + emails.ultimoToken(email, "verificar-email") + "\"}", null).andExpect(status().isNoContent());
    }

    Conta ong() throws Exception {
        String email = email();
        String id = corpo(call(HttpMethod.POST, "/api/auth/registro/ong", """
                {"email":"%s","senha":"%s","cnpj":"%s","nivelCapacidade":"NIVEL_1"}
                """.formatted(email, SENHA, TestCnpjs.com("55")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        verificar(email);
        call(HttpMethod.POST, "/api/ongs/" + id + "/verificar", null, login("admin@teste.local", "girassol-azul-teste-42"))
                .andExpect(status().isOk());
        return new Conta(id, email, login(email, SENHA));
    }

    Conta empresa() throws Exception {
        String email = email();
        String id = corpo(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, SENHA, TestCnpjs.com("11")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        verificar(email);
        return new Conta(id, email, login(email, SENHA));
    }

    String candidato(Conta ong, String nome, String extras) throws Exception {
        String j = """
                {"nomeCompleto":"%s","dataNascimento":"%s","cep":"%s","modeloTrabalhoPref":"PRESENCIAL",
                 "consentimentoConfirmado":true %s}
                """.formatted(nome, LocalDate.now().minusYears(30), CEP, extras == null ? "" : "," + extras);
        return corpo(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", j, ong.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    String vaga(Conta emp, String extras) throws Exception {
        String j = """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s" %s}
                """.formatted(CEP, extras == null ? "" : "," + extras);
        return corpo(call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas", j, emp.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    JsonNode sugerir(Conta emp, String vaga) throws Exception {
        return corpo(call(HttpMethod.POST, "/api/vagas/" + vaga + "/matches/sugestoes?raioKm=15&limite=200", null, emp.token())
                .andExpect(status().isCreated()));
    }

    String matchDe(JsonNode lista, String nome) {
        for (JsonNode m : lista) if (m.get("candidatoNome").asText().equals(nome)) return m.get("id").asText();
        throw new AssertionError("match de " + nome + " não encontrado");
    }

    // ================================================================ termos
    @Test
    void termosDesatualizadosBloqueiamAteAceitar() throws Exception {
        Conta emp = empresa();
        jdbc.update("update usuarios set termos_versao = '2020-01' where email = ?", emp.email());

        call(HttpMethod.GET, "/api/empresas/" + emp.id() + "/vagas", null, emp.token())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("TERMOS_DESATUALIZADOS"));
        // a conta segue acessível para ver e aceitar
        call(HttpMethod.GET, "/api/conta/termos", null, emp.token())
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendente").value(true));
        call(HttpMethod.POST, "/api/conta/termos/aceitar", null, emp.token()).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/empresas/" + emp.id() + "/vagas", null, emp.token()).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/conta/termos", null, emp.token()).andExpect(jsonPath("$.pendente").value(false));
    }

    // ================================================================ notificações
    @Test
    void funilGeraAvisosPorEmailSemDadosPessoais() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "Fulana Notificada", null);
        String v = vaga(emp, null);

        JsonNode novas = sugerir(emp, v);
        assertEquals(1, emails.quantos(ong.email(), "Novas sugestões"), "um aviso por ONG");
        String corpoAviso = emails.para(ong.email()).stream().filter(e -> e.assunto().contains("Novas sugestões"))
                .findFirst().orElseThrow().corpo();
        assertFalse(corpoAviso.contains("Fulana"), "e-mail não cita nome de candidato");
        assertTrue(corpoAviso.contains("/conta"), "traz o caminho para desligar os avisos");

        String match = matchDe(corpo(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches", null, ong.token())
                .andExpect(status().isOk())), "Fulana Notificada");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());
        assertEquals(1, emails.quantos(emp.email(), "enviou um candidato"));

        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", emp.token()).andExpect(status().isOk());
        assertEquals(1, emails.quantos(ong.email(), "entrevista"));

        // a ONG desliga os avisos: a contratação não gera e-mail
        call(HttpMethod.PUT, "/api/conta/notificacoes", "{\"ativas\":false}", ong.token()).andExpect(status().isNoContent());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"CONTRATADO\"}", emp.token()).andExpect(status().isOk());
        assertEquals(0, emails.quantos(ong.email(), "contratado"));
        assertNotNull(novas);
    }

    // ================================================================ vaga: editar e excluir
    @Test
    void editarVagaDescartaSugestoesVelhas() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "EditaVaga A", null);
        String v = vaga(emp, null);
        assertTrue(sugerir(emp, v).size() >= 1);

        JsonNode editada = corpo(call(HttpMethod.PUT, "/api/vagas/" + v, """
                {"nivelCargo":"COORDENADOR","tipoContrato":"FIXO","modeloTrabalho":"HIBRIDO","cepAtuacao":"%s",
                 "escolaridadeMinima":"MEDIO","skills":["excel"]}
                """.formatted(CEP), emp.token()).andExpect(status().isOk()));
        assertEquals("HIBRIDO", editada.get("modeloTrabalho").asText());
        assertEquals("MEDIO", editada.get("escolaridadeMinima").asText());
        assertEquals("Excel", editada.get("skills").get(0).asText(), "habilidade padronizada pelo catálogo");

        // as sugestões antigas foram descartadas; a busca refaz com os requisitos novos
        call(HttpMethod.GET, "/api/vagas/" + v + "/matches", null, emp.token()).andExpect(jsonPath("$.length()").value(0));
        assertTrue(sugerir(emp, v).size() >= 1);
    }

    @Test
    void naoEditaVagaComCandidatoEnviado_eSoExcluiSemCandidatosEmAndamento() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "ExcluiVaga A", null);
        String v = vaga(emp, null);
        JsonNode lista = sugerir(emp, v);
        String match = matchDe(lista, "ExcluiVaga A");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());

        String corpoEdicao = """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s"}
                """.formatted(CEP);
        call(HttpMethod.PUT, "/api/vagas/" + v, corpoEdicao, emp.token()).andExpect(status().isUnprocessableEntity());
        call(HttpMethod.DELETE, "/api/vagas/" + v, null, emp.token()).andExpect(status().isUnprocessableEntity());

        // vaga só com sugestões: pode excluir; some da lista
        String v2 = vaga(emp, null);
        sugerir(emp, v2);
        call(HttpMethod.DELETE, "/api/vagas/" + v2, null, emp.token()).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/vagas/" + v2, null, emp.token()).andExpect(status().isForbidden());   // não revela existência
    }

    @Test
    void outraEmpresaNaoEditaNemExcluiVaga() throws Exception {
        Conta dona = empresa();
        Conta outra = empresa();
        String v = vaga(dona, null);
        call(HttpMethod.DELETE, "/api/vagas/" + v, null, outra.token()).andExpect(status().isForbidden());
        call(HttpMethod.PUT, "/api/vagas/" + v, """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s"}
                """.formatted(CEP), outra.token()).andExpect(status().isForbidden());
    }

    // ================================================================ candidato: editar
    @Test
    void editarCandidatoPorONG() throws Exception {
        Conta ong = ong();
        Conta outra = ong();
        String id = candidato(ong, "Edita Candidato", "\"tags\":[{\"categoria\":\"SKILL\",\"tag\":\"excel\"}]");
        String edicao = """
                {"nomeCompleto":"Edita Candidato Novo","dataNascimento":"%s","cep":"%s","modeloTrabalhoPref":"REMOTO",
                 "escolaridade":"MEDIO","regimes":["CLT"],
                 "tags":[{"categoria":"SKILL","tag":"atendimento ao CLIENTE"},{"categoria":"VULNERABILIDADE","tag":"PCD"}]}
                """.formatted(LocalDate.now().minusYears(31), CEP);

        JsonNode r = corpo(call(HttpMethod.PUT, "/api/ongs/" + ong.id() + "/candidatos/" + id, edicao, ong.token())
                .andExpect(status().isOk()));
        assertEquals("Edita Candidato Novo", r.get("nomeCompleto").asText());
        assertEquals("REMOTO", r.get("modeloTrabalhoPref").asText());
        String tags = r.get("tags").toString();
        assertTrue(tags.contains("Atendimento ao cliente"), "skill padronizada: " + tags);
        assertFalse(tags.contains("excel"), "as tags antigas foram substituídas");

        // salvar de novo com as MESMAS tags e regimes não pode violar a unicidade (regressão)
        call(HttpMethod.PUT, "/api/ongs/" + ong.id() + "/candidatos/" + id, edicao, ong.token()).andExpect(status().isOk());
        call(HttpMethod.PUT, "/api/ongs/" + ong.id() + "/candidatos/" + id, edicao, ong.token()).andExpect(status().isOk());

        // outra ONG não edita; menor de 18 é recusado
        call(HttpMethod.PUT, "/api/ongs/" + ong.id() + "/candidatos/" + id, edicao, outra.token()).andExpect(status().isForbidden());
        call(HttpMethod.PUT, "/api/ongs/" + ong.id() + "/candidatos/" + id,
                edicao.replace(LocalDate.now().minusYears(31).toString(), LocalDate.now().minusYears(17).toString()), ong.token())
                .andExpect(status().isUnprocessableEntity());
    }

    // ================================================================ skills
    @Test
    void catalogoDeSkillsEPadronizacao() throws Exception {
        Conta emp = empresa();
        JsonNode c = corpo(call(HttpMethod.GET, "/api/cardapio/skills", null, emp.token()).andExpect(status().isOk()));
        assertTrue(c.toString().contains("\"Excel\""));
        // texto livre fora do catálogo é mantido, sem duplicar variações do mesmo nome
        String v = vaga(emp, "\"skills\":[\"EXCEL\",\"excél\",\"Soldagem MIG\"]");
        JsonNode vv = corpo(call(HttpMethod.GET, "/api/vagas/" + v, null, emp.token()).andExpect(status().isOk()));
        assertEquals(2, vv.get("skills").size());
        assertTrue(vv.get("skills").toString().contains("Soldagem MIG"));
    }
}
