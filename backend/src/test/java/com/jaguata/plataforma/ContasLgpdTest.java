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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contas (verificação de e-mail, senha, sessões) e LGPD (termos, consentimento, exportar/anonimizar, auditoria). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class ContasLgpdTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "cavalo-bateria-grampo-7";
    static final String NOVA = "pinguim-lanterna-cacto-9";
    static final String ADMIN_EMAIL = "admin@teste.local";
    static final String ADMIN_SENHA = "girassol-azul-teste-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- helpers
    ResultActions call(HttpMethod m, String url, String corpo, String token) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url);
        if (corpo != null) b.contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    JsonNode corpo(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    static String email() {
        return "u" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    record Conta(String id, String email) {
    }

    record Tokens(String access, String refresh) {
    }

    Conta registrar(String tipo, String cnpj) throws Exception {
        String e = email();
        String extra = tipo.equals("ong") ? ",\"nivelCapacidade\":\"NIVEL_1\"" : "";
        String id = corpo(call(HttpMethod.POST, "/api/auth/registro/" + tipo, """
                {"email":"%s","senha":"%s","cnpj":"%s","aceiteTermos":true%s}
                """.formatted(e, SENHA, cnpj, extra), null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificacaoPendente").value(true))).get("entidadeId").asText();
        return new Conta(id, e);
    }

    Conta empresaNaoVerificada() throws Exception {
        return registrar("empresa", TestCnpjs.com("11"));
    }

    void verificar(Conta c) throws Exception {
        call(HttpMethod.POST, "/api/auth/email/verificar",
                "{\"token\":\"" + emails.ultimoToken(c.email(), "verificar-email") + "\"}", null)
                .andExpect(status().isNoContent());
    }

    Conta empresa() throws Exception {
        Conta c = empresaNaoVerificada();
        verificar(c);
        return c;
    }

    Conta ong() throws Exception {
        Conta c = registrar("ong", TestCnpjs.com("55"));
        verificar(c);
        return c;
    }

    Tokens login(String email, String senha) throws Exception {
        JsonNode n = corpo(call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha), null).andExpect(status().isOk()));
        return new Tokens(n.get("accessToken").asText(), n.get("refreshToken").asText());
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return call(HttpMethod.POST, "/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}", null);
    }

    String admin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_SENHA).access();
    }

    void envelhecerTokens(String email) {
        jdbc.update("update tokens_conta set criado_em = now() - interval '5 minutes' "
                + "where usuario_id = (select id from usuarios where email = ?)", email);
    }

    // ================================================================ termos
    @Test
    void cadastroExigeAceiteDosTermos() throws Exception {
        String sem = "{\"email\":\"%s\",\"senha\":\"%s\",\"cnpj\":\"%s\"}".formatted(email(), SENHA, TestCnpjs.com("11"));
        call(HttpMethod.POST, "/api/auth/registro/empresa", sem, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.aceiteTermos").exists());
        String recusado = "{\"email\":\"%s\",\"senha\":\"%s\",\"cnpj\":\"%s\",\"aceiteTermos\":false}"
                .formatted(email(), SENHA, TestCnpjs.com("11"));
        call(HttpMethod.POST, "/api/auth/registro/empresa", recusado, null).andExpect(status().isBadRequest());
    }

    @Test
    void aceiteFicaRegistradoComAVersaoDosTermos() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        call(HttpMethod.GET, "/api/conta/dados", null, t.access())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termosVersao").value("2026-11"))
                .andExpect(jsonPath("$.termosAceitosEm").exists());
    }

    // ================================================================ verificação de e-mail
    @Test
    void loginFicaBloqueadoAteVerificarOEmail() throws Exception {
        Conta c = empresaNaoVerificada();
        assertEquals(1, emails.quantos(c.email(), "Confirme seu e-mail"));
        assertTrue(emails.para(c.email()).get(0).corpo().contains("http://localhost:3000/verificar-email?token="));

        // senha certa + e-mail não verificado: 403 com código estável
        call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(c.email(), SENHA), null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("EMAIL_NAO_VERIFICADO"));
        // senha errada continua 401: o login não revela se o e-mail existe nem se foi verificado
        call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"errada-errada-1\"}".formatted(c.email()), null)
                .andExpect(status().isUnauthorized());

        verificar(c);
        Tokens t = login(c.email(), SENHA);
        assertFalse(t.refresh().isBlank());
    }

    @Test
    void tokenDeVerificacaoEhDeUsoUnicoEExpira() throws Exception {
        Conta a = empresaNaoVerificada();
        String token = emails.ultimoToken(a.email(), "verificar-email");
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + token + "\"}", null).andExpect(status().isNoContent());
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + token + "\"}", null).andExpect(status().isBadRequest());

        Conta b = empresaNaoVerificada();
        String tokenB = emails.ultimoToken(b.email(), "verificar-email");
        jdbc.update("update tokens_conta set expira_em = now() - interval '1 hour' "
                + "where usuario_id = (select id from usuarios where email = ?)", b.email());
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + tokenB + "\"}", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("inválido ou expirado")));
        call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(b.email(), SENHA), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenInventadoOuDeOutroTipoEhRecusado() throws Exception {
        Conta c = empresa();
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"nao-existe\"}", null).andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/api/auth/senha/esqueci", "{\"email\":\"" + c.email() + "\"}", null).andExpect(status().isAccepted());
        String reset = emails.ultimoToken(c.email(), "redefinir-senha");
        // token de redefinição não serve como verificação de e-mail
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + reset + "\"}", null).andExpect(status().isBadRequest());
    }

    @Test
    void reenvioTemIntervaloMinimoEInvalidaOLinkAnterior() throws Exception {
        Conta c = empresaNaoVerificada();
        String primeiro = emails.ultimoToken(c.email(), "verificar-email");

        call(HttpMethod.POST, "/api/auth/email/reenviar", "{\"email\":\"" + c.email() + "\"}", null).andExpect(status().isAccepted());
        assertEquals(1, emails.quantos(c.email(), "Confirme seu e-mail"), "reenvio imediato é contido (1 por minuto)");

        envelhecerTokens(c.email());
        call(HttpMethod.POST, "/api/auth/email/reenviar", "{\"email\":\"" + c.email() + "\"}", null).andExpect(status().isAccepted());
        assertEquals(2, emails.quantos(c.email(), "Confirme seu e-mail"));
        String segundo = emails.ultimoToken(c.email(), "verificar-email");
        assertNotEquals(primeiro, segundo);

        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + primeiro + "\"}", null).andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/api/auth/email/verificar", "{\"token\":\"" + segundo + "\"}", null).andExpect(status().isNoContent());
    }

    @Test
    void reenvioEEsqueciRespondemIgualParaEmailInexistente() throws Exception {
        String fantasma = "fantasma" + email();
        for (String rota : new String[]{"/api/auth/email/reenviar", "/api/auth/senha/esqueci"}) {
            call(HttpMethod.POST, rota, "{\"email\":\"" + fantasma + "\"}", null)
                    .andExpect(status().isAccepted()).andExpect(content().string(""));
        }
        assertTrue(emails.para(fantasma).isEmpty());

        Conta real = empresa();
        call(HttpMethod.POST, "/api/auth/senha/esqueci", "{\"email\":\"" + real.email() + "\"}", null)
                .andExpect(status().isAccepted()).andExpect(content().string(""));   // mesma resposta
    }

    // ================================================================ recuperação de senha
    @Test
    void redefinirSenhaRespeitaAPoliticaEEncerraTodasAsSessoes() throws Exception {
        Conta c = empresa();
        Tokens antiga = login(c.email(), SENHA);

        call(HttpMethod.POST, "/api/auth/senha/esqueci", "{\"email\":\"" + c.email() + "\"}", null).andExpect(status().isAccepted());
        String token = emails.ultimoToken(c.email(), "redefinir-senha");
        assertNotNull(token);

        // senha fraca: recusada SEM gastar o link
        call(HttpMethod.POST, "/api/auth/senha/redefinir", "{\"token\":\"%s\",\"novaSenha\":\"password123\"}".formatted(token), null)
                .andExpect(status().isUnprocessableEntity());
        call(HttpMethod.POST, "/api/auth/senha/redefinir", "{\"token\":\"%s\",\"novaSenha\":\"%s\"}".formatted(token, NOVA), null)
                .andExpect(status().isNoContent());

        // link de uso único
        call(HttpMethod.POST, "/api/auth/senha/redefinir", "{\"token\":\"%s\",\"novaSenha\":\"outra-senha-forte-12\"}".formatted(token), null)
                .andExpect(status().isBadRequest());
        // a senha antiga morreu, a nova vale, e a sessão antiga foi derrubada
        call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(c.email(), SENHA), null)
                .andExpect(status().isUnauthorized());
        login(c.email(), NOVA);
        refresh(antiga.refresh()).andExpect(status().isUnauthorized());
        // aviso de segurança por e-mail
        assertEquals(1, emails.quantos(c.email(), "foi alterada"));
    }

    @Test
    void esqueciSenhaDeContaNaoVerificadaReenviaAVerificacaoNaoOLinkDeSenha() throws Exception {
        Conta c = empresaNaoVerificada();
        envelhecerTokens(c.email());
        call(HttpMethod.POST, "/api/auth/senha/esqueci", "{\"email\":\"" + c.email() + "\"}", null).andExpect(status().isAccepted());
        assertEquals(2, emails.quantos(c.email(), "Confirme seu e-mail"));
        assertNull(emails.ultimoToken(c.email(), "redefinir-senha"));
    }

    // ================================================================ sessões (refresh rotativo)
    @Test
    void refreshRotacionaEReusoDeTokenAntigoDerrubaAFamilia() throws Exception {
        Conta c = empresa();
        Tokens a = login(c.email(), SENHA);

        JsonNode n = corpo(refresh(a.refresh()).andExpect(status().isOk()));
        Tokens b = new Tokens(n.get("accessToken").asText(), n.get("refreshToken").asText());
        assertNotEquals(a.refresh(), b.refresh());
        call(HttpMethod.GET, "/api/conta/dados", null, b.access()).andExpect(status().isOk());

        // quem apresenta o token antigo (ex.: copiado por um invasor) é recusado...
        refresh(a.refresh()).andExpect(status().isUnauthorized());
        // ...e a sessão inteira é revogada, inclusive o token novo
        refresh(b.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshInexistenteOuExpiradoEhRecusado() throws Exception {
        refresh("token-que-nao-existe").andExpect(status().isUnauthorized());
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        jdbc.update("update sessoes set expira_em = now() - interval '1 minute' "
                + "where usuario_id = (select id from usuarios where email = ?)", c.email());
        refresh(t.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaOTokenDeRenovacao() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"" + t.refresh() + "\"}", null).andExpect(status().isNoContent());
        refresh(t.refresh()).andExpect(status().isUnauthorized());
        // logout é idempotente e não revela nada sobre tokens desconhecidos
        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"" + t.refresh() + "\"}", null).andExpect(status().isNoContent());
        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"qualquer-coisa\"}", null).andExpect(status().isNoContent());
    }

    @Test
    void sairDeTodosOsDispositivosEncerraTodasAsSessoes() throws Exception {
        Conta c = empresa();
        Tokens t1 = login(c.email(), SENHA);
        Tokens t2 = login(c.email(), SENHA);
        call(HttpMethod.GET, "/api/conta/dados", null, t1.access()).andExpect(jsonPath("$.sessoesAtivas").value(2));

        call(HttpMethod.POST, "/api/conta/sessoes/encerrar", null, t1.access()).andExpect(status().isNoContent());
        refresh(t1.refresh()).andExpect(status().isUnauthorized());
        refresh(t2.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenFicaSoComoHashNoBanco() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        Integer comOTokenEmClaro = jdbc.queryForObject("select count(*) from sessoes where token_hash = ?", Integer.class, t.refresh());
        assertEquals(0, comOTokenEmClaro);
        Integer comHash = jdbc.queryForObject("select count(*) from sessoes where token_hash = ?", Integer.class,
                com.jaguata.plataforma.conta.Hashes.sha256(t.refresh()));
        assertEquals(1, comHash);
    }

    // ================================================================ troca de senha logado
    @Test
    void alterarSenhaExigeASenhaAtualEEncerraAsSessoes() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);

        call(HttpMethod.POST, "/api/conta/senha", "{\"senhaAtual\":\"errada-errada-1\",\"novaSenha\":\"%s\"}".formatted(NOVA), t.access())
                .andExpect(status().isUnprocessableEntity());
        call(HttpMethod.POST, "/api/conta/senha", "{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(SENHA, SENHA), t.access())
                .andExpect(status().isUnprocessableEntity());                         // igual à atual
        call(HttpMethod.POST, "/api/conta/senha", "{\"senhaAtual\":\"%s\",\"novaSenha\":\"password123\"}".formatted(SENHA), t.access())
                .andExpect(status().isUnprocessableEntity());                         // política de senha
        call(HttpMethod.POST, "/api/conta/senha", "{\"senhaAtual\":\"%s\",\"novaSenha\":\"%s\"}".formatted(SENHA, NOVA), t.access())
                .andExpect(status().isNoContent());

        refresh(t.refresh()).andExpect(status().isUnauthorized());
        login(c.email(), NOVA);
        call(HttpMethod.POST, "/api/conta/senha", "{\"senhaAtual\":\"x\",\"novaSenha\":\"%s\"}".formatted(NOVA), null)
                .andExpect(status().isUnauthorized());
    }

    // ================================================================ direitos do titular (conta)
    @Test
    void contaExportaOsProprioDadosSemSegredos() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        String resposta = call(HttpMethod.GET, "/api/conta/dados", null, t.access())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(c.email()))
                .andExpect(jsonPath("$.papel").value("EMPRESA"))
                .andExpect(jsonPath("$.emailVerificado").value(true))
                .andExpect(jsonPath("$.empresa.razaoSocial").exists())
                .andReturn().getResponse().getContentAsString();
        assertFalse(resposta.toLowerCase().contains("hash"), "nunca expõe hash de senha ou de token");
        assertFalse(resposta.contains("$2a$"));
    }

    @Test
    void excluirContaExigeSenhaEApagaOAcesso() throws Exception {
        Conta c = empresa();
        Tokens t = login(c.email(), SENHA);
        String usuarioId = corpo(call(HttpMethod.GET, "/api/conta/dados", null, t.access())).get("id").asText();

        call(HttpMethod.DELETE, "/api/conta", "{\"senha\":\"errada-errada-1\"}", t.access()).andExpect(status().isUnprocessableEntity());
        call(HttpMethod.DELETE, "/api/conta", "{\"senha\":\"" + SENHA + "\"}", t.access()).andExpect(status().isNoContent());

        call(HttpMethod.POST, "/api/auth/login", "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(c.email(), SENHA), null)
                .andExpect(status().isUnauthorized());
        refresh(t.refresh()).andExpect(status().isUnauthorized());
        assertEquals(1, emails.quantos(c.email(), "foi excluída"));
        Integer restos = jdbc.queryForObject("select (select count(*) from sessoes where usuario_id = ?::uuid) "
                + "+ (select count(*) from tokens_conta where usuario_id = ?::uuid)", Integer.class, usuarioId, usuarioId);
        assertEquals(0, restos, "sessões e tokens saem em cascata");

        // o registro de auditoria sobrevive (sem dados pessoais)
        call(HttpMethod.GET, "/api/admin/auditoria?acao=CONTA_EXCLUIDA", null, admin())
                .andExpect(jsonPath("$.itens[?(@.entidadeId == '" + usuarioId + "')]").isNotEmpty());
    }

    @Test
    void administradorNaoSeExcluiPorAqui() throws Exception {
        call(HttpMethod.DELETE, "/api/conta", "{\"senha\":\"" + ADMIN_SENHA + "\"}", admin()).andExpect(status().isUnprocessableEntity());
        login(ADMIN_EMAIL, ADMIN_SENHA);
    }

    // ================================================================ consentimento e direitos do candidato
    String candidatoJson(String nome, boolean consentimento) {
        return """
                {"nomeCompleto":"%s","dataNascimento":"%s","cep":"13015904","modeloTrabalhoPref":"PRESENCIAL",
                 "tags":[{"categoria":"VULNERABILIDADE","tag":"PCD"}],"consentimentoConfirmado":%b}
                """.formatted(nome, LocalDate.now().minusYears(30).minusDays(1), consentimento);
    }

    String ongVerificadaToken(Conta ong) throws Exception {
        call(HttpMethod.POST, "/api/admin/ongs/" + ong.id() + "/verificar", null, admin()).andExpect(status().isOk());
        return login(ong.email(), SENHA).access();
    }

    @Test
    void cadastroDeCandidatoExigeConsentimentoERegistraQuemAtestou() throws Exception {
        Conta ong = ong();
        String token = ongVerificadaToken(ong);

        call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson("Ana Lima", false), token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.consentimentoConfirmado").exists());
        String id = corpo(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson("Ana Lima", true), token)
                .andExpect(status().isCreated())).get("id").asText();

        var linha = jdbc.queryForMap("select consentimento_versao, consentimento_em, consentimento_usuario_id "
                + "from candidatos where id = ?::uuid", id);
        assertEquals("2026-11", linha.get("consentimento_versao"));
        assertNotNull(linha.get("consentimento_em"));
        assertNotNull(linha.get("consentimento_usuario_id"));
    }

    @Test
    void candidatoPodeSerListadoExportadoEAnonimizadoPelaOng() throws Exception {
        Conta ong = ong();
        String token = ongVerificadaToken(ong);
        Conta outraOng = ong();
        String tokenOutra = ongVerificadaToken(outraOng);
        Conta empresa = empresa();
        String tokenEmpresa = login(empresa.email(), SENHA).access();

        String candidato = corpo(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson("Ana Lima", true).replace("13015904", "01001000"), token)
                .andExpect(status().isCreated())).get("id").asText();
        // vaga e match com o candidato (Sé: só este teste usa esse CEP, então o resultado é isolado)
        String vaga = corpo(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas",
                "{\"nivelCargo\":\"ANALISTA\",\"tipoContrato\":\"FIXO\",\"modeloTrabalho\":\"PRESENCIAL\",\"cepAtuacao\":\"01001000\"}",
                tokenEmpresa).andExpect(status().isCreated())).get("id").asText();
        call(HttpMethod.POST, "/api/vagas/" + vaga + "/matches/sugestoes?raioKm=10", null, tokenEmpresa)
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(1)));

        // lista paginada da ONG (sem localização exata)
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].nomeCompleto").value("Ana Lima"))
                .andExpect(jsonPath("$.itens[0].tags[0].tag").value("PCD"))
                .andExpect(jsonPath("$.itens[0].cep").doesNotExist())
                .andExpect(jsonPath("$.itens[0].lat").doesNotExist());

        // exportação completa
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + candidato + "/dados", null, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Lima"))
                .andExpect(jsonPath("$.cep").value("01001000"))
                .andExpect(jsonPath("$.consentimentoVersao").value("2026-11"))
                .andExpect(jsonPath("$.participacoes", hasSize(1)))
                .andExpect(jsonPath("$.participacoes[0].status").value("SUGERIDO"));

        // outra ONG não toca nos dados: 403 na rota alheia, 404 ao tentar o id alheio na própria rota
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos", null, tokenOutra).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/ongs/" + outraOng.id() + "/candidatos/" + candidato + "/dados", null, tokenOutra).andExpect(status().isNotFound());
        call(HttpMethod.DELETE, "/api/ongs/" + outraOng.id() + "/candidatos/" + candidato, null, tokenOutra).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + candidato + "/dados", null, tokenEmpresa).andExpect(status().isForbidden());

        // anonimização
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + candidato, null, token).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos", null, token).andExpect(jsonPath("$.total").value(0));
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + candidato + "/dados", null, token).andExpect(status().isNotFound());
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + candidato, null, token).andExpect(status().isNotFound());

        var linha = jdbc.queryForMap("select nome_completo, cep, lat, lng, data_nascimento, anonimizado_em, "
                + "(select count(*) from candidato_tags t where t.candidato_id = c.id) tags from candidatos c where id = ?::uuid", candidato);
        assertEquals("Candidato removido", linha.get("nome_completo"));
        assertEquals("00000000", linha.get("cep"));
        assertEquals(0.0, ((Number) linha.get("lat")).doubleValue());
        assertEquals(0L, ((Number) linha.get("tags")).longValue());
        assertNotNull(linha.get("anonimizado_em"));

        // a pendência foi encerrada e o candidato não volta a ser sugerido
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/matches?status=RECUSADO", null, token).andExpect(jsonPath("$", hasSize(1)));
        String vaga2 = corpo(call(HttpMethod.POST, "/api/empresas/" + empresa.id() + "/vagas",
                "{\"nivelCargo\":\"ASSISTENTE\",\"tipoContrato\":\"FIXO\",\"modeloTrabalho\":\"PRESENCIAL\",\"cepAtuacao\":\"01001000\"}",
                tokenEmpresa).andExpect(status().isCreated())).get("id").asText();
        call(HttpMethod.POST, "/api/vagas/" + vaga2 + "/matches/sugestoes?raioKm=10", null, tokenEmpresa)
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(0)));
    }

    // ================================================================ auditoria
    @Test
    void auditoriaRegistraAcoesSensiveisSemDadosPessoais() throws Exception {
        Conta ong = ong();
        String token = ongVerificadaToken(ong);
        String candidato = corpo(call(HttpMethod.POST, "/api/ongs/" + ong.id() + "/candidatos", candidatoJson("Zuleica Quintanilha", true), token)
                .andExpect(status().isCreated())).get("id").asText();
        call(HttpMethod.GET, "/api/ongs/" + ong.id() + "/candidatos/" + candidato + "/dados", null, token).andExpect(status().isOk());
        call(HttpMethod.DELETE, "/api/ongs/" + ong.id() + "/candidatos/" + candidato, null, token).andExpect(status().isNoContent());

        String admin = admin();
        for (String acao : new String[]{"CANDIDATO_CADASTRADO", "CANDIDATO_EXPORTADO", "CANDIDATO_ANONIMIZADO"}) {
            call(HttpMethod.GET, "/api/admin/auditoria?acao=" + acao, null, admin)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[?(@.entidadeId == '" + candidato + "')]").isNotEmpty())
                    .andExpect(jsonPath("$.itens[?(@.entidadeId == '" + candidato + "')].papel").value(org.hamcrest.Matchers.hasItem("ONG")));
        }
        call(HttpMethod.GET, "/api/admin/auditoria?acao=ONG_VERIFICADA", null, admin)
                .andExpect(jsonPath("$.itens[?(@.entidadeId == '" + ong.id() + "')]").isNotEmpty());

        String tudo = call(HttpMethod.GET, "/api/admin/auditoria?tamanho=100", null, admin).andReturn().getResponse().getContentAsString();
        assertFalse(tudo.contains("Zuleica"), "a trilha de auditoria nunca guarda nomes");
        assertFalse(tudo.contains(ong.email()), "nem e-mails");
    }

    @Test
    void auditoriaEhSoParaAdmin() throws Exception {
        Conta c = empresa();
        String token = login(c.email(), SENHA).access();
        call(HttpMethod.GET, "/api/admin/auditoria", null, token).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/admin/auditoria", null, null).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/admin/auditoria?tamanho=5", null, admin())
                .andExpect(status().isOk()).andExpect(jsonPath("$.tamanho").value(5));
    }

    @Test
    void cadastroELoginFicamRegistradosNaAuditoria() throws Exception {
        Conta c = empresa();
        login(c.email(), SENHA);
        String usuarioId = jdbc.queryForObject("select id::text from usuarios where email = ?", String.class, c.email());
        for (String acao : new String[]{"REGISTRO", "EMAIL_VERIFICADO", "LOGIN"}) {
            Integer n = jdbc.queryForObject("select count(*) from auditoria where acao = ? and usuario_id = ?::uuid",
                    Integer.class, acao, usuarioId);
            assertTrue(n >= 1, "faltou auditar " + acao);
        }
    }
}
