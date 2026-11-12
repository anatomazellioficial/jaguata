package com.jaguata.plataforma;

import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.security.ClientIp;
import com.jaguata.plataforma.security.RateLimiter;
import com.jaguata.plataforma.security.SenhaPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SegurancaUnitTest {

    // ---------- RateLimiter ----------
    @Test
    void limitador_bloqueiaAposACapacidadeEReabastecePeloTempo() {
        AtomicLong agora = new AtomicLong();
        RateLimiter rl = new RateLimiter(agora::get);
        Duration minuto = Duration.ofMinutes(1);

        for (int i = 0; i < 3; i++) assertTrue(rl.tryConsume("k", 3, minuto).allowed());
        RateLimiter.Result negado = rl.tryConsume("k", 3, minuto);
        assertFalse(negado.allowed());
        assertTrue(negado.retryAfterSeconds() >= 1 && negado.retryAfterSeconds() <= 21);

        agora.addAndGet(Duration.ofSeconds(21).toNanos());          // 1 token a cada 20 s
        assertTrue(rl.tryConsume("k", 3, minuto).allowed());
        assertFalse(rl.tryConsume("k", 3, minuto).allowed());
    }

    @Test
    void limitador_naoAcumulaAlemDaCapacidadeEIsolaChaves() {
        AtomicLong agora = new AtomicLong();
        RateLimiter rl = new RateLimiter(agora::get);
        agora.addAndGet(Duration.ofHours(10).toNanos());
        for (int i = 0; i < 2; i++) assertTrue(rl.tryConsume("a", 2, Duration.ofMinutes(1)).allowed());
        assertFalse(rl.tryConsume("a", 2, Duration.ofMinutes(1)).allowed());
        assertTrue(rl.tryConsume("b", 2, Duration.ofMinutes(1)).allowed());   // outra chave, outro balde
    }

    // ---------- ClientIp ----------
    private MockHttpServletRequest req(String remote, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void ip_ignoraXForwardedForSemProxyConfiavel() {
        assertEquals("9.9.9.9", ClientIp.resolve(req("9.9.9.9", "1.2.3.4"), 0));
    }

    @Test
    void ip_usaEntradaDaDireitaEIgnoraAsForjadasAEsquerda() {
        // o cliente forjou "6.6.6.6"; o proxy confiável anexou o IP real "5.5.5.5"
        assertEquals("5.5.5.5", ClientIp.resolve(req("10.0.0.1", "6.6.6.6, 5.5.5.5"), 1));
        assertEquals("6.6.6.6", ClientIp.resolve(req("10.0.0.1", "6.6.6.6, 5.5.5.5"), 2));
    }

    @Test
    void ip_voltaParaEnderecoDaConexaoQuandoCabecalhoFaltaOuEhCurto() {
        assertEquals("10.0.0.1", ClientIp.resolve(req("10.0.0.1", null), 1));
        assertEquals("10.0.0.1", ClientIp.resolve(req("10.0.0.1", "5.5.5.5"), 3));
    }

    // ---------- Política de senha ----------
    @Test
    void senha_aceitaSenhaForte() {
        assertDoesNotThrow(() -> SenhaPolicy.validar("cavalo-bateria-grampo-7", "ana@x.com"));
    }

    @Test
    void senha_rejeitaCurtaComumRepetidaLongaDemaisOuComEmail() {
        assertThrows(RegraNegocioException.class, () -> SenhaPolicy.validar("curta123", "a@x.com"));
        assertThrows(RegraNegocioException.class, () -> SenhaPolicy.validar("Password123", "a@x.com"));
        assertThrows(RegraNegocioException.class, () -> SenhaPolicy.validar("aaaaaaaaaaaa", "a@x.com"));
        assertThrows(RegraNegocioException.class, () -> SenhaPolicy.validar("x".repeat(40) + "é".repeat(20), "a@x.com"));
        assertThrows(RegraNegocioException.class, () -> SenhaPolicy.validar("joaquim.silva-2026", "joaquim.silva@x.com"));
    }
}
