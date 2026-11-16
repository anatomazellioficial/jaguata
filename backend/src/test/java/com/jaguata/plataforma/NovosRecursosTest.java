package com.jaguata.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguata.plataforma.service.NotificacaoService;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Resumo diário e preferências por tipo de aviso, exclusão definitiva, cadastro sem enumeração de e-mails,
 * relatórios de impacto, criação/busca em lote com distribuição entre ONGs e filtro de idade (desligado por padrão).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class NovosRecursosTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";
    static final String CAMPINAS = "13015904";
    static final String SE = "01001000";
    static final String PAULISTA = "01310100";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificacaoService notificacoes;

    record Conta(String id, String email, String token) {
    }

    ResultActions call(HttpMethod m, String url, String body, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (body != null && url.startsWith("/api/auth/registro/") && !body.contains("aceiteTermos")) {
            body = body.replaceFirst("\\{", "{\"aceiteTermos\":true,");
        }
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
        return "n" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
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

    String candidato(Conta ong, String nome, int idade, String cep, String extras) throws Exception {
        String j = """
                {"nomeCompleto":"%s","dataNascimento":"%s","cep":"%s","modeloTrabalhoPref":"PRESENCIAL",
                 "consentimentoConfirmado":true %s}
                """.formatted(nome, LocalDate.now().minusYears(idade).minusDays(10), cep, extras == null ? "" : "," + extras);
        return corpo(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", j, ong.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    String vaga(Conta emp, String cep, String extras) throws Exception {
        String j = """
                {"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s" %s}
                """.formatted(cep, extras == null ? "" : "," + extras);
        return corpo(call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas", j, emp.token())
                .andExpect(status().isCreated())).get("id").asText();
    }

    JsonNode sugerir(Conta emp, String vaga, String query) throws Exception {
        return corpo(call(HttpMethod.POST, "/api/vagas/" + vaga + "/matches/sugestoes?raioKm=15&limite=200"
                + (query == null ? "" : "&" + query), null, emp.token()).andExpect(status().isCreated()));
    }

    String matchDe(JsonNode lista, String nome) {
        for (JsonNode m : lista) if (m.get("candidatoNome").asText().equals(nome)) return m.get("id").asText();
        throw new AssertionError("match de " + nome + " não encontrado");
    }

    // ================================================================ preferências e resumo diário
    @Test
    void preferenciaPorTipoEResumoDiario() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        candidato(ong, "Resumo Pref A", 30, CAMPINAS, null);
        String v = vaga(emp, CAMPINAS, null);

        // a empresa desliga só "andamento": não recebe o aviso de candidato enviado
        call(HttpMethod.PUT, "/api/conta/notificacoes", "{\"andamento\":false}", emp.token()).andExpect(status().isNoContent());
        // a ONG escolhe o resumo diário: nada chega na hora
        call(HttpMethod.PUT, "/api/conta/notificacoes", "{\"resumoDiario\":true}", ong.token()).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/conta/dados", null, ong.token()).andExpect(jsonPath("$.notifResumoDiario").value(true));

        JsonNode lista = sugerir(emp, v, null);
        assertEquals(0, emails.quantos(ong.email(), "Novas sugestões"), "com resumo diário, nada é enviado na hora");
        String match = matchDe(corpo(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches", null, ong.token())
                .andExpect(status().isOk())), "Resumo Pref A");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());
        assertEquals(0, emails.quantos(emp.email(), "enviou um candidato"), "tipo desligado: sem aviso");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", emp.token()).andExpect(status().isOk());
        assertEquals(0, emails.quantos(ong.email(), "entrevista"), "resumo diário: segura o aviso de entrevista");

        // o agendador roda: um único e-mail de resumo para a ONG, e a fila esvazia
        int enviados = notificacoes.enviarResumos();
        assertTrue(enviados >= 1);
        assertEquals(1, emails.quantos(ong.email(), "resumo diário"));
        String corpoResumo = emails.para(ong.email()).stream().filter(e -> e.assunto().contains("resumo diário"))
                .findFirst().orElseThrow().corpo();
        assertTrue(corpoResumo.contains("nova sugestão") || corpoResumo.contains("novas sugestões"));
        assertTrue(corpoResumo.contains("convocação"));
        assertFalse(corpoResumo.contains("Resumo Pref"), "o resumo não cita nome de candidato");
        assertEquals(0, notificacoes.enviarResumos(), "fila vazia: nada a enviar");
        assertNotNull(lista);
    }

    // ================================================================ exclusão definitiva
    @Test
    void exclusaoDefinitivaSoSemContratacao() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        String livre = candidato(ong, "Excluir Livre", 30, CAMPINAS, null);
        String contratado = candidato(ong, "Excluir Contratado", 30, CAMPINAS, null);
        String v = vaga(emp, CAMPINAS, null);
        JsonNode ms = sugerir(emp, v, null);
        String match = matchDe(corpo(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches", null, ong.token())
                .andExpect(status().isOk())), "Excluir Contratado");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", emp.token()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"CONTRATADO\"}", emp.token()).andExpect(status().isOk());

        // quem foi contratado não pode ser apagado de vez (preserva o histórico): só anonimizado
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + contratado + "?definitivo=true", null, ong.token())
                .andExpect(status().isUnprocessableEntity());

        // sem contratação: some de verdade (banco e matches)
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + livre + "?definitivo=true", null, ong.token())
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from candidatos where id = ?::uuid", Integer.class, livre));
        assertEquals(0, jdbc.queryForObject("select count(*) from matches where candidato_id = ?::uuid", Integer.class, livre));
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + livre, null, ong.token()).andExpect(status().isNotFound());
        assertTrue(jdbc.queryForObject("select count(*) from auditoria where acao = 'CANDIDATO_EXCLUIDO'", Integer.class) >= 1);

        // o padrão continua sendo a anonimização (a linha permanece, sem dados pessoais)
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + contratado, null, ong.token())
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("select count(*) from candidatos where id = ?::uuid and anonimizado_em is not null",
                Integer.class, contratado));
        assertNotNull(ms);
    }

    // ================================================================ cadastro sem enumeração
    @Test
    void cadastroComEmailExistenteResponde201IgualEAvisaODono() throws Exception {
        Conta dono = empresa();
        long antes = jdbc.queryForObject("select count(*) from usuarios", Long.class);

        JsonNode r = corpo(call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(dono.email().toUpperCase(), SENHA, TestCnpjs.com("11")), null).andExpect(status().isCreated()));
        assertEquals(dono.email(), r.get("email").asText());
        assertTrue(r.has("entidadeId"));
        assertEquals(antes, jdbc.queryForObject("select count(*) from usuarios", Long.class), "nenhuma conta nova foi criada");
        assertEquals(1, emails.quantos(dono.email(), "Tentativa de cadastro"));

        // idem para ONG
        JsonNode o = corpo(call(HttpMethod.POST, "/api/auth/registro/ong", """
                {"email":"%s","senha":"%s","cnpj":"%s","nivelCapacidade":"NIVEL_1"}
                """.formatted(dono.email(), SENHA, TestCnpjs.com("55")), null).andExpect(status().isCreated()));
        assertTrue(o.has("entidadeId"));
        assertEquals(antes, jdbc.queryForObject("select count(*) from usuarios", Long.class));
    }

    // ================================================================ relatórios de impacto
    @Test
    void relatoriosDeImpactoSoTotais() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        Conta outra = empresa();
        candidato(ong, "Impacto A", 30, CAMPINAS, "\"genero\":\"MULHER\",\"racaCor\":\"PARDA\"");
        String v = vaga(emp, CAMPINAS, "\"gruposPrioritarios\":[\"MULHERES\"],\"justificativaAfirmativa\":\"Programa de equidade de gênero da empresa\"");
        sugerir(emp, v, null);
        String match = matchDe(corpo(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches", null, ong.token())
                .andExpect(status().isOk())), "Impacto A");
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENVIADO\"}", ong.token()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"ENTREVISTA\"}", emp.token()).andExpect(status().isOk());
        call(HttpMethod.PATCH, "/api/matches/" + match + "/status", "{\"status\":\"CONTRATADO\"}", emp.token()).andExpect(status().isOk());

        JsonNode e = corpo(call(HttpMethod.GET, "/api/empresas/" + emp.id() + "/impacto", null, emp.token()).andExpect(status().isOk()));
        assertEquals(1, e.get("vagasTotal").asInt());
        assertEquals(1, e.get("vagasPreenchidas").asInt());
        assertEquals(1, e.get("contratacoes").asInt());
        assertEquals(1, e.get("ongsParceiras").asInt());
        assertEquals(1, e.get("vagasAfirmativas").asInt());
        assertEquals(1, e.get("contratacoesEmVagasAfirmativas").asInt());
        assertEquals(1, e.get("contratacoesPorOng").size());
        assertFalse(e.get("tempoMedioAteContratacaoDias").isNull());

        JsonNode o = corpo(call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/impacto", null, ong.token()).andExpect(status().isOk()));
        assertEquals(1, o.get("contratacoes").asInt());
        assertEquals(1, o.get("empresasParceiras").asInt());
        assertEquals(100.0, o.get("taxaColocacaoPct").asDouble(), 0.01);
        assertTrue(o.get("contratacoesPorSetor").size() >= 1);

        // só totais: nada de gênero, raça/cor, grupos ou nomes
        String txt = (e.toString() + o.toString()).toLowerCase();
        for (String proibido : new String[]{"genero", "racacor", "mulher", "parda", "impacto a", "pcd", "grupo"}) {
            assertFalse(txt.contains(proibido), "o relatório não deve conter: " + proibido);
        }

        // posse: outra empresa/ONG não vê
        call(HttpMethod.GET, "/api/empresas/" + emp.id() + "/impacto", null, outra.token()).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/impacto", null, emp.token()).andExpect(status().isForbidden());
    }

    // ================================================================ lote e distribuição
    @Test
    void criaVagasEmLoteEBuscaSemRepetirCandidatos() throws Exception {
        Conta ong = ong();
        Conta emp = empresa();
        for (int i = 1; i <= 3; i++) candidato(ong, "Lote Cand " + i, 30, PAULISTA, null);

        JsonNode criadas = corpo(call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas/lote", """
                {"quantidade":3,"vaga":{"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s"}}
                """.formatted(PAULISTA), emp.token()).andExpect(status().isCreated()));
        assertEquals(3, criadas.size());

        call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas/lote", """
                {"quantidade":21,"vaga":{"nivelCargo":"ANALISTA","tipoContrato":"FIXO","modeloTrabalho":"PRESENCIAL","cepAtuacao":"%s"}}
                """.formatted(PAULISTA), emp.token()).andExpect(status().isBadRequest());

        JsonNode r = corpo(call(HttpMethod.POST, "/api/empresas/" + emp.id() + "/vagas/sugestoes-em-lote?raioKm=10&limite=50",
                null, emp.token()).andExpect(status().isCreated()));
        assertEquals(3, r.size());

        // cada candidato aparece em UMA só das 3 vagas
        Set<String> vistos = new HashSet<>();
        for (JsonNode vaga : criadas) {
            JsonNode lista = corpo(call(HttpMethod.GET, "/api/vagas/" + vaga.get("id").asText() + "/matches", null, emp.token())
                    .andExpect(status().isOk()));
            for (JsonNode m : lista) assertTrue(vistos.add(m.get("candidatoId").asText()), "candidato repetido entre vagas do lote");
        }
        assertTrue(vistos.size() >= 3);

        // sem vagas abertas: erro claro
        Conta vazia = empresa();
        call(HttpMethod.POST, "/api/empresas/" + vazia.id() + "/vagas/sugestoes-em-lote", null, vazia.token())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void distribuirReparteSugestoesEntreAsOngs() throws Exception {
        Conta ong1 = ong();
        Conta ong2 = ong();
        Conta emp = empresa();
        for (int i = 1; i <= 3; i++) candidato(ong1, "Dist A" + i, 30, SE, null);
        for (int i = 1; i <= 3; i++) candidato(ong2, "Dist B" + i, 30, SE, null);
        String v = vaga(emp, SE, null);

        JsonNode lista = corpo(call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=5&limite=4&distribuir=true",
                null, emp.token()).andExpect(status().isCreated()));
        assertEquals(4, lista.size());
        Set<String> ongs = new HashSet<>();
        long daOng1 = 0;
        for (JsonNode m : lista) {
            ongs.add(m.get("ongId").asText());
            if (m.get("ongId").asText().equals(ong1.id())) daOng1++;
        }
        assertTrue(ongs.contains(ong1.id()) && ongs.contains(ong2.id()), "as duas ONGs aparecem");
        assertEquals(2, daOng1, "4 sugestões repartidas 2 a 2");
    }

    // ================================================================ filtro de idade
    @Test
    void filtroDeIdadeEstaDesligadoPorPadrao() throws Exception {
        Conta emp = empresa();
        String v = vaga(emp, CAMPINAS, null);
        call(HttpMethod.GET, "/api/cardapio/recursos", null, emp.token()).andExpect(jsonPath("$.filtroIdade").value(false));
        call(HttpMethod.POST, "/api/vagas/" + v + "/matches/sugestoes?raioKm=5&idadeMin=30", null, emp.token())
                .andExpect(status().isUnprocessableEntity());
    }
}
