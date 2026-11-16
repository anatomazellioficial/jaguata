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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Matching v2: elegibilidade, filtros, formação/skills/regime, vagas afirmativas, áreas da ONG. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class MatchingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";
    static final String ADMIN_EMAIL = "admin@teste.local";
    static final String ADMIN_SENHA = "girassol-azul-teste-42";
    static final String CEP = "13015904";   // Campinas

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;

    record Conta(String id, String token) {
    }

    ResultActions call(HttpMethod m, String url, String body, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (body != null && url.startsWith("/api/auth/registro/")) {
            body = body.replaceFirst("\\{", "{\"aceiteTermos\":true,");
        }
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    String login(String email, String senha) throws Exception {
        return body(call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha), null)
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    String email() {
        return "m" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    void verificar(String email) throws Exception {
        String token = emails.ultimoToken(email, "verificar-email");
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + token + "\"}", null)
                .andExpect(status().isNoContent());
    }

    Conta ong() throws Exception {
        String email = email();
        String id = body(call(HttpMethod.POST, "/api/auth/registro/ong", """
                {"email":"%s","senha":"%s","cnpj":"%s","nivelCapacidade":"NIVEL_1"}
                """.formatted(email, SENHA, TestCnpjs.com("55")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        verificar(email);
        call(HttpMethod.POST, "/api/ongs/" + id + "/verificar", null, login(ADMIN_EMAIL, ADMIN_SENHA)).andExpect(status().isOk());
        return new Conta(id, login(email, SENHA));
    }

    Conta empresa() throws Exception {
        String email = email();
        String id = body(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, SENHA, TestCnpjs.com("11")), null).andExpect(status().isCreated())).get("entidadeId").asText();
        verificar(email);
        return new Conta(id, login(email, SENHA));
    }

    /** Cadastra um candidato; {@code extras} são campos JSON adicionais (sem vírgula inicial). */
    String candidato(Conta ong, String nome, int idade, String extras) throws Exception {
        String json = """
                {"nomeCompleto":"%s","dataNascimento":"%s","cep":"%s","modeloTrabalhoPref":"PRESENCIAL",
                 "consentimentoConfirmado":true %s}
                """.formatted(nome, LocalDate.now().minusYears(idade).minusDays(1), CEP,
                extras == null || extras.isBlank() ? "" : "," + extras);
        return body(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", json, ong.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    String vaga(Conta empresa, String nivel, String extras) throws Exception {
        String json = """
                {"nivelCargo":"%s","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s" %s}
                """.formatted(nivel, CEP, extras == null || extras.isBlank() ? "" : "," + extras);
        return body(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas", json, empresa.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    JsonNode sugestoes(Conta empresa, String vaga, String query) throws Exception {
        return body(call(HttpMethod.POST, "/api/vagas/" + vaga + "/matches/sugestoes?raioKm=15&limite=200"
                + (query == null ? "" : "&" + query), null, empresa.token()).andExpect(status().isCreated()));
    }

    static List<String> nomes(JsonNode matches) {
        List<String> l = new ArrayList<>();
        matches.forEach(m -> l.add(m.get("candidatoNome").asText()));
        return l;
    }

    static JsonNode de(JsonNode matches, String nome) {
        for (JsonNode m : matches) if (m.get("candidatoNome").asText().equals(nome)) return m;
        return null;
    }

    @Test
    void estagioExigeTecnicoOuSuperiorEmAndamento() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "EstMedio", 25, "\"escolaridade\":\"MEDIO\"");
        candidato(ong, "EstSuperior", 25, "\"escolaridade\":\"SUPERIOR_INCOMPLETO\"");
        candidato(ong, "EstSemInfo", 25, null);
        List<String> n = nomes(sugestoes(emp, vaga(emp, "ESTAGIARIO", null), null));
        assertTrue(n.contains("EstSuperior"));
        assertFalse(n.contains("EstMedio"));
        assertFalse(n.contains("EstSemInfo"));
    }

    @Test
    void filtroEscolaridadeMinimaExcluiAbaixo() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "EscMedio", 30, "\"escolaridade\":\"MEDIO\"");
        candidato(ong, "EscSuperior", 30, "\"escolaridade\":\"SUPERIOR\"");
        String v = vaga(emp, "ANALISTA", null);
        List<String> n = nomes(sugestoes(emp, v, "escolaridadeMin=SUPERIOR"));
        assertTrue(n.contains("EscSuperior"));
        assertFalse(n.contains("EscMedio"));
        assertTrue(nomes(sugestoes(emp, v, null)).contains("EscMedio"), "sem filtro, ninguém é excluído");
    }

    @Test
    void filtrosDeSkillsERegime() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "SkExcel", 30, """
                "regimes":["CLT"],"tags":[{"categoria":"SKILL","tag":"Excel"},{"categoria":"SKILL","tag":"Atendimento"}]""");
        candidato(ong, "SkNenhuma", 30, "\"regimes\":[\"PJ\"]");
        String extras = "\"skills\":[\"excel\",\"atendimento\"],\"regime\":\"CLT\"";
        // cada consulta persiste as sugestões da vaga: uma vaga nova por cenário

        JsonNode todos = sugestoes(emp, vaga(emp, "ANALISTA", extras), null);
        double comSkills = de(todos, "SkExcel").get("matchScore").asDouble();
        double semSkills = de(todos, "SkNenhuma").get("matchScore").asDouble();
        assertTrue(comSkills > semSkills);

        List<String> porSkill = nomes(sugestoes(emp, vaga(emp, "ANALISTA", extras), "skills=Excel,ATENDIMENTO"));
        assertTrue(porSkill.contains("SkExcel"));
        assertFalse(porSkill.contains("SkNenhuma"));

        List<String> porRegime = nomes(sugestoes(emp, vaga(emp, "ANALISTA", extras), "regime=PJ"));
        assertTrue(porRegime.contains("SkNenhuma"));
        assertFalse(porRegime.contains("SkExcel"));
    }

    @Test
    void respostaTrazMotivosESoma100() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "MotivoA", 30, "\"escolaridade\":\"MEDIO\"");
        JsonNode m = de(sugestoes(emp, vaga(emp, "ANALISTA", "\"escolaridadeMinima\":\"MEDIO\""), null), "MotivoA");
        assertNotNull(m);
        JsonNode motivos = m.get("motivos");
        assertEquals(7, motivos.size());
        int max = 0;
        double pts = 0;
        for (JsonNode x : motivos) {
            max += x.get("maximo").asInt();
            pts += x.get("pontos").asDouble();
        }
        assertEquals(100, max);
        assertEquals(m.get("matchScore").asDouble(), pts, 0.01);
    }

    @Test
    void vagaAfirmativaExigeJustificativa() throws Exception {
        Conta emp = empresa();
        String semJust = """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s",
                 "gruposPrioritarios":["MULHERES"]}
                """.formatted(CEP);
        call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas", semJust, emp.token())
                .andExpect(status().isUnprocessableEntity());
        vaga(emp, "ANALISTA", "\"gruposPrioritarios\":[\"MULHERES\"],\"justificativaAfirmativa\":\"Programa de equidade de gênero da empresa\"");
    }

    @Test
    void vagaAfirmativaSoAumentaPontuacao_eNaoExpoeGeneroNemRaca() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "AfMulher", 30, "\"genero\":\"MULHER\",\"racaCor\":\"PARDA\"");
        candidato(ong, "AfHomem", 30, "\"genero\":\"HOMEM\",\"racaCor\":\"BRANCA\"");
        String v = vaga(emp, "ANALISTA",
                "\"gruposPrioritarios\":[\"MULHERES\"],\"justificativaAfirmativa\":\"Programa de equidade de gênero da empresa\"");
        ResultActions r = call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=15&limite=200", null, emp.token())
                .andExpect(status().isCreated());
        JsonNode todos = body(r);
        double mulher = de(todos, "AfMulher").get("matchScore").asDouble();
        double homem = de(todos, "AfHomem").get("matchScore").asDouble();
        assertEquals(10.0, mulher - homem, 0.01, "grupo priorizado ganha os pontos de PRIORIDADE");
        assertNotNull(de(todos, "AfHomem"), "ninguém é excluído por não pertencer ao grupo");

        String texto = todos.toString().toLowerCase();
        assertFalse(texto.contains("\"genero\""));
        assertFalse(texto.contains("racacor"));
        assertFalse(texto.contains("parda"));

        // a lista de candidatos da ONG também não expõe as autodeclarações
        String lista = call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos", null, ong.token())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString().toLowerCase();
        assertFalse(lista.contains("genero"));
        assertFalse(lista.contains("racacor"));
    }

    @Test
    void autodeclaracaoSaiNaExportacaoDoTitularENaAnonimizacaoSomeTudo() throws Exception {
        Conta ong = ong();
        String id = candidato(ong, "ExpPerfil", 30, "\"genero\":\"NAO_BINARIO\",\"escolaridade\":\"TECNICO\"");
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + id + "/dados", null, ong.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genero").value("NAO_BINARIO"))
                .andExpect(jsonPath("$.escolaridade").value("TECNICO"));
    }

    @Test
    void areasDeAtuacaoSoAOngDonaAltera() throws Exception {
        Conta a = ong();
        Conta b = ong();
        call(HttpMethod.PUT, "/api/ongs/" + a.id() + "/setores", "{\"setores\":[\"EDUCACAO\",\"COMERCIO\"]}", a.token())
                .andExpect(status().isOk());
        call(HttpMethod.GET, "/api/ongs/" + a.id() + "/setores", null, a.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        call(HttpMethod.PUT, "/api/ongs/" + a.id() + "/setores", "{\"setores\":[\"EDUCACAO\"]}", b.token())
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogoDePerfil() throws Exception {
        Conta ong = ong();
        JsonNode c = body(call(HttpMethod.GET, "/api/cardapio/perfil", null, ong.token()).andExpect(status().isOk()));
        for (String k : List.of("escolaridades", "regimes", "setores", "grupos", "generos", "racas")) {
            assertTrue(c.get(k).size() > 0, k);
        }
    }
}
