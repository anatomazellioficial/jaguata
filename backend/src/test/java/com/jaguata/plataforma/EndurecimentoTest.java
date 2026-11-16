package com.jaguata.plataforma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguata.plataforma.security.Totp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Endurecimento: revogação imediata do access token, 2FA (TOTP) dos administradores e tolerância a renovações
 * simultâneas do refresh token (várias instâncias do BFF).
 */
@SpringBootTest(properties = "app.security.refresh-grace-segundos=10")
@AutoConfigureMockMvc
@Import({IntegracoesStub.class, EndurecimentoTest.RelogioDeTeste.class})
@Testcontainers
class EndurecimentoTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "senha-forte-123";
    static final String ADMIN_EMAIL = "admin@teste.local";
    static final String ADMIN_SENHA = "girassol-azul-teste-42";

    /** Relógio que o teste adianta à vontade. */
    static final AtomicReference<Instant> AGORA = new AtomicReference<>(Instant.parse("2030-01-01T12:00:00Z"));

    @TestConfiguration
    static class RelogioDeTeste {
        @Bean
        @Primary
        Clock relogioControlado() {
            return new Clock() {
                @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return AGORA.get(); }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CapturaEmails emails;

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

    ResultActions login(String email, String senha, String codigo) throws Exception {
        String extra = codigo == null ? "" : ",\"codigoTotp\":\"" + codigo + "\"";
        return call(HttpMethod.POST, "/api/auth/login",
                "{\"email\":\"%s\",\"senha\":\"%s\"%s}".formatted(email, senha, extra), null);
    }

    record Sessao(String access, String refresh) {
    }

    Sessao entrar(String email, String senha) throws Exception {
        JsonNode n = corpo(login(email, senha, null).andExpect(status().isOk()));
        return new Sessao(n.get("accessToken").asText(), n.get("refreshToken").asText());
    }

    String novaEmpresa() throws Exception {
        String email = "e" + System.nanoTime() + "@teste.local";
        call(HttpMethod.POST, "/api/auth/registro/empresa", """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, SENHA, TestCnpjs.com("11")), null).andExpect(status().isCreated());
        call(HttpMethod.POST, "/api/auth/email/verificar",
                "{\"token\":\"" + emails.ultimoToken(email, "verificar-email") + "\"}", null).andExpect(status().isNoContent());
        return email;
    }

    static byte[] decodificarBase32(String s) {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            buffer = (buffer << 5) | alfabeto.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    static String codigo(byte[] segredo, long deslocamentoDePassos) {
        return Totp.codigo(segredo, Totp.passoAtual(AGORA.get().getEpochSecond()) + deslocamentoDePassos);
    }

    // ================================================================ revogação imediata do access token
    @Test
    void logoutInvalidaOAccessTokenNaHora() throws Exception {
        String email = novaEmpresa();
        Sessao s = entrar(email, SENHA);
        call(HttpMethod.GET, "/api/conta/dados", null, s.access()).andExpect(status().isOk());

        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"" + s.refresh() + "\"}", null)
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/conta/dados", null, s.access()).andExpect(status().isUnauthorized());
    }

    @Test
    void trocaDeSenhaInvalidaTodosOsAccessTokens() throws Exception {
        String email = novaEmpresa();
        Sessao a = entrar(email, SENHA);
        Sessao b = entrar(email, SENHA);
        call(HttpMethod.POST, "/api/conta/senha",
                "{\"senhaAtual\":\"%s\",\"novaSenha\":\"outra-senha-bem-forte-9\"}".formatted(SENHA), a.access())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/conta/dados", null, a.access()).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/conta/dados", null, b.access()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEncerraSoAquelaSessao() throws Exception {
        String email = novaEmpresa();
        Sessao a = entrar(email, SENHA);
        Sessao b = entrar(email, SENHA);
        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"" + a.refresh() + "\"}", null)
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/conta/dados", null, a.access()).andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/conta/dados", null, b.access()).andExpect(status().isOk());
    }

    // ================================================================ refresh simultâneo
    @Test
    void duasRenovacoesSimultaneasNaoDerrubamASessao() throws Exception {
        String email = novaEmpresa();
        Sessao s = entrar(email, SENHA);
        String corpo = "{\"refreshToken\":\"" + s.refresh() + "\"}";

        JsonNode primeira = corpo(call(HttpMethod.POST, "/api/auth/refresh", corpo, null).andExpect(status().isOk()));
        // a "outra instância" apresenta o mesmo token logo em seguida
        JsonNode segunda = corpo(call(HttpMethod.POST, "/api/auth/refresh", corpo, null).andExpect(status().isOk()));
        assertNotEquals(primeira.get("refreshToken").asText(), segunda.get("refreshToken").asText());

        // os dois pares seguem válidos
        call(HttpMethod.GET, "/api/conta/dados", null, primeira.get("accessToken").asText()).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/conta/dados", null, segunda.get("accessToken").asText()).andExpect(status().isOk());
        call(HttpMethod.POST, "/api/auth/refresh", "{\"refreshToken\":\"" + segunda.get("refreshToken").asText() + "\"}", null)
                .andExpect(status().isOk());
    }

    @Test
    void reusoAposLogoutContinuaRecusado() throws Exception {
        String email = novaEmpresa();
        Sessao s = entrar(email, SENHA);
        String corpo = "{\"refreshToken\":\"" + s.refresh() + "\"}";
        JsonNode nova = corpo(call(HttpMethod.POST, "/api/auth/refresh", corpo, null).andExpect(status().isOk()));
        call(HttpMethod.POST, "/api/auth/logout", "{\"refreshToken\":\"" + nova.get("refreshToken").asText() + "\"}", null)
                .andExpect(status().isNoContent());
        // o token antigo está dentro da janela de tolerância, mas a sessão foi encerrada de propósito
        call(HttpMethod.POST, "/api/auth/refresh", corpo, null).andExpect(status().isUnauthorized());
    }

    // ================================================================ 2FA (TOTP)
    @Test
    void segundoFatorDoAdministrador() throws Exception {
        Sessao admin = entrar(ADMIN_EMAIL, ADMIN_SENHA);

        // só administradores configuram 2FA
        String empresa = novaEmpresa();
        call(HttpMethod.POST, "/api/conta/2fa/configurar", null, entrar(empresa, SENHA).access())
                .andExpect(status().isForbidden());

        call(HttpMethod.GET, "/api/conta/2fa", null, admin.access()).andExpect(jsonPath("$.ativo").value(false));
        JsonNode cfg = corpo(call(HttpMethod.POST, "/api/conta/2fa/configurar", null, admin.access()).andExpect(status().isOk()));
        assertTrue(cfg.get("otpauthUri").asText().startsWith("otpauth://totp/"));
        byte[] segredo = decodificarBase32(cfg.get("segredo").asText());

        // código errado não ativa; o certo ativa
        call(HttpMethod.POST, "/api/conta/2fa/ativar", "{\"codigo\":\"000000\"}", admin.access())
                .andExpect(status().isUnprocessableEntity());
        call(HttpMethod.POST, "/api/conta/2fa/ativar", "{\"codigo\":\"%s\"}".formatted(codigo(segredo, 0)), admin.access())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/conta/2fa", null, admin.access()).andExpect(jsonPath("$.ativo").value(true));

        // login agora exige o código
        login(ADMIN_EMAIL, ADMIN_SENHA, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOTP_REQUERIDO"));
        login(ADMIN_EMAIL, ADMIN_SENHA, "123456").andExpect(status().isUnauthorized());
        login(ADMIN_EMAIL, "senha-errada-qualquer", codigo(segredo, 1)).andExpect(status().isUnauthorized());

        // o código do próximo passo (relógio adiantado) entra; reaproveitá-lo é recusado (replay)
        AGORA.set(AGORA.get().plusSeconds(60));
        String valido = codigo(segredo, 0);
        login(ADMIN_EMAIL, ADMIN_SENHA, valido).andExpect(status().isOk());
        login(ADMIN_EMAIL, ADMIN_SENHA, valido).andExpect(status().isUnauthorized());

        // desativar exige senha E código novo
        AGORA.set(AGORA.get().plusSeconds(60));
        call(HttpMethod.POST, "/api/conta/2fa/desativar",
                "{\"senha\":\"errada-errada\",\"codigo\":\"%s\"}".formatted(codigo(segredo, 0)), admin.access())
                .andExpect(status().isUnprocessableEntity());
        AGORA.set(AGORA.get().plusSeconds(60));
        call(HttpMethod.POST, "/api/conta/2fa/desativar",
                "{\"senha\":\"%s\",\"codigo\":\"%s\"}".formatted(ADMIN_SENHA, codigo(segredo, 0)), admin.access())
                .andExpect(status().isNoContent());
        login(ADMIN_EMAIL, ADMIN_SENHA, null).andExpect(status().isOk());
    }

    @Test
    void totpBateComOVetorDaRfc6238() {
        // Apêndice B da RFC 6238 (SHA-1, segredo "12345678901234567890"), truncado em 6 dígitos
        byte[] segredo = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("287082", Totp.codigo(segredo, 59 / 30));
        assertEquals("081804", Totp.codigo(segredo, 1111111109L / 30));
        assertEquals("050471", Totp.codigo(segredo, 1111111111L / 30));
        assertEquals("005924", Totp.codigo(segredo, 1234567890L / 30));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32(segredo));
    }
}
