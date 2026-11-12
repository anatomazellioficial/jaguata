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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobertura de segurança com limites BAIXOS. Cada teste usa IPs próprios via X-Forwarded-For
 * (o backend confia em 1 proxy aqui), para que os baldes de um não interfiram nos outros.
 */
@SpringBootTest(properties = {
        "app.security.rate-limit.login-per-minute=3",
        "app.security.rate-limit.registro-per-hour=2",
        "app.security.rate-limit.api-per-minute=5",
        "app.security.rate-limit.consulta-per-minute=3",
        "app.security.rate-limit.trusted-proxy-hops=1",
        "app.security.login-max-failures=3",
        "app.security.max-body-bytes=2048"
})
@AutoConfigureMockMvc
@Import(IntegracoesStub.class)
@Testcontainers
class SegurancaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static final String SENHA = "cavalo-bateria-grampo-7";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;

    // ---------- helpers ----------
    ResultActions call(HttpMethod m, String url, String body, String token, String ip) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(m, url).header("X-Forwarded-For", ip);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    static String cnpj() {
        return TestCnpjs.com("11");
    }

    static String email() {
        return "u" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + "@teste.local";
    }

    static String empresaJson(String email, String senha) {
        return """
                {"email":"%s","senha":"%s","cnpj":"%s"}
                """.formatted(email, senha, cnpj());
    }

    static String loginJson(String email, String senha) {
        return "{\"email\":\"%s\",\"senha\":\"%s\"}".formatted(email, senha);
    }

    /** Registra e loga uma empresa usando IPs exclusivos (respeitando os limites baixos). */
    String tokenDeEmpresa(String ipBase) throws Exception {
        String e = email();
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson(e, SENHA), null, ipBase + ".1")
                .andExpect(status().isCreated());
        JsonNode n = json.readTree(call(HttpMethod.POST, "/api/auth/login", loginJson(e, SENHA), null, ipBase + ".2")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return n.get("accessToken").asText();
    }

    String jwtAssinado(String issuer, String papel) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .issuedAt(agora).expiresAt(agora.plusSeconds(600)).claim("papel", papel).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    // ---------- rate limit ----------
    @Test
    void loginTemLimiteDeTaxaPorIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            call(HttpMethod.POST, "/api/auth/login", loginJson(email(), "qualquer-senha-1"), null, "10.1.0.1")
                    .andExpect(status().isUnauthorized());
        }
        call(HttpMethod.POST, "/api/auth/login", loginJson(email(), "qualquer-senha-1"), null, "10.1.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));
        // outro IP não é afetado
        call(HttpMethod.POST, "/api/auth/login", loginJson(email(), "qualquer-senha-1"), null, "10.1.0.2")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void xForwardedForForjadoNaoBurlaOLimite() throws Exception {
        // o atacante prefixa IPs falsos; a entrada da direita (do proxy confiável) é a que vale
        for (int i = 0; i < 3; i++) {
            call(HttpMethod.POST, "/api/auth/login", loginJson(email(), "qualquer-senha-1"), null, "1.1.1." + i + ", 10.1.1.1")
                    .andExpect(status().isUnauthorized());
        }
        call(HttpMethod.POST, "/api/auth/login", loginJson(email(), "qualquer-senha-1"), null, "2.2.2.2, 10.1.1.1")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void registroTemLimiteDeTaxaPorIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            call(HttpMethod.POST, "/api/auth/registro/empresa", "{}", null, "10.3.0.1").andExpect(status().isBadRequest());
        }
        call(HttpMethod.POST, "/api/auth/registro/empresa", "{}", null, "10.3.0.1")
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void apiTemLimitePorUsuarioEMudarDeIpNaoAjuda() throws Exception {
        String token = tokenDeEmpresa("10.4.0");
        for (int i = 0; i < 5; i++) {
            call(HttpMethod.GET, "/api/cardapio/cargos", null, token, "10.4.1." + i).andExpect(status().isOk());
        }
        call(HttpMethod.GET, "/api/cardapio/cargos", null, token, "10.4.9.9").andExpect(status().isTooManyRequests());
        // outro usuário, mesmo IP: balde próprio
        String outro = tokenDeEmpresa("10.4.5");
        call(HttpMethod.GET, "/api/cardapio/cargos", null, outro, "10.4.9.9").andExpect(status().isOk());
    }

    @Test
    void consultasExternasTemLimiteProprioPorIp() throws Exception {
        String cnpj = TestCnpjs.com("11");
        for (int i = 0; i < 3; i++) {
            call(HttpMethod.GET, "/api/consulta/cnpj/" + cnpj, null, null, "10.9.0.1").andExpect(status().isOk());
        }
        call(HttpMethod.GET, "/api/consulta/cnpj/" + cnpj, null, null, "10.9.0.1")
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        // outro IP não é afetado
        call(HttpMethod.GET, "/api/consulta/cep/01001000", null, null, "10.9.0.2").andExpect(status().isOk());
    }

    // ---------- bloqueio de conta ----------
    @Test
    void contaFicaBloqueadaAposFalhasSeguidasInclusiveComSenhaCerta() throws Exception {
        String e = email();
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson(e, SENHA), null, "10.2.0.9")
                .andExpect(status().isCreated());
        for (int i = 1; i <= 3; i++) {   // IPs diferentes: o bloqueio é por conta, não por IP
            call(HttpMethod.POST, "/api/auth/login", loginJson(e, "senha-errada-xyz"), null, "10.2.0." + i)
                    .andExpect(status().isUnauthorized());
        }
        call(HttpMethod.POST, "/api/auth/login", loginJson(e, SENHA), null, "10.2.0.4")
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void loginBemSucedidoZeraOContadorDeFalhas() throws Exception {
        String e = email();
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson(e, SENHA), null, "10.2.1.9")
                .andExpect(status().isCreated());
        for (int rodada = 0; rodada < 2; rodada++) {
            call(HttpMethod.POST, "/api/auth/login", loginJson(e, "senha-errada-xyz"), null, "10.2.1." + (rodada * 2))
                    .andExpect(status().isUnauthorized());
            call(HttpMethod.POST, "/api/auth/login", loginJson(e, "senha-errada-xyz"), null, "10.2.1." + (rodada * 2 + 1))
                    .andExpect(status().isUnauthorized());
            call(HttpMethod.POST, "/api/auth/login", loginJson(e, SENHA), null, "10.2.2." + rodada)
                    .andExpect(status().isOk());      // 2 falhas + sucesso, duas vezes: nunca chega a 3 seguidas
        }
    }

    // ---------- cabeçalhos / entrada ----------
    @Test
    void respostasTemCabecalhosDeSeguranca() throws Exception {
        call(HttpMethod.GET, "/api/cardapio/cargos", null, null, "10.5.0.1")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void corpoGrandeDemaisEhRecusadoCom413() throws Exception {
        String grande = "{\"email\":\"a@b.com\",\"senha\":\"" + "x".repeat(5000) + "\"}";
        call(HttpMethod.POST, "/api/auth/login", grande, null, "10.6.0.1").andExpect(status().isPayloadTooLarge());
    }

    @Test
    void tipoDeConteudoNaoSuportadoRetorna415NaoE500() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").header("X-Forwarded-For", "10.6.0.2")
                        .contentType(MediaType.TEXT_PLAIN).content("oi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- política de senha ----------
    @Test
    void senhaFracaEhRecusada() throws Exception {
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson(email(), "curta"), null, "10.7.0.1")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.senha").exists());
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson(email(), "password123"), null, "10.7.0.2")
                .andExpect(status().isUnprocessableEntity());
        call(HttpMethod.POST, "/api/auth/registro/empresa", empresaJson("joaquim.silva@t.dev", "joaquim.silva-2026"), null, "10.7.0.3")
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- JWT ----------
    @Test
    void tokenComEmissorErradoEhRecusadoMasOCertoPassa() throws Exception {
        call(HttpMethod.GET, "/api/admin/resumo", null, jwtAssinado("evil", "ADMIN"), "10.8.0.1")
                .andExpect(status().isUnauthorized());
        call(HttpMethod.GET, "/api/admin/resumo", null, jwtAssinado("jaguata", "ADMIN"), "10.8.0.2")
                .andExpect(status().isOk());
    }

    @Test
    void tokenSemAssinaturaAlgNoneEhRecusado() throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"iss\":\"jaguata\",\"sub\":\"x\",\"papel\":\"ADMIN\",\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}").getBytes(StandardCharsets.UTF_8));
        call(HttpMethod.GET, "/api/admin/resumo", null, header + "." + payload + ".", "10.8.0.3")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAssinadoComOutroSegredoEhRecusado() throws Exception {
        // mesmo formato e claims válidos, mas HMAC calculado com uma chave diferente
        var key = new javax.crypto.spec.SecretKeySpec("outro-segredo-totalmente-diferente-123456".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var outro = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableSecret<>(key));
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("jaguata").subject("x").issuedAt(agora)
                .expiresAt(agora.plusSeconds(600)).claim("papel", "ADMIN").build();
        String forjado = outro.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        call(HttpMethod.GET, "/api/admin/resumo", null, forjado, "10.8.0.4").andExpect(status().isUnauthorized());
    }
}
