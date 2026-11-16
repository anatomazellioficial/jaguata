package com.jaguata.plataforma.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bloqueio temporário por conta após falhas seguidas de login (defesa contra força bruta
 * distribuída, que o limite por IP não pega). A chave é o e-mail normalizado, exista ele ou não,
 * para não revelar quais e-mails estão cadastrados.
 * Com {@code app.security.rate-limit.store=redis} o contador é compartilhado entre réplicas (senão cada instância
 * teria o seu, multiplicando as tentativas do atacante). Se o Redis falhar, usa o contador local.
 * Contrapartida conhecida: alguém pode bloquear a conta de outra pessoa por alguns minutos.
 */
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Cache<String, AtomicInteger> falhas;
    private final int maxFalhas;
    private final Duration janela;
    private final StringRedisTemplate redis;   // nulo quando store=memory

    public LoginAttemptService(SecurityProperties props, ObjectProvider<StringRedisTemplate> redisProvider) {
        this.maxFalhas = props.loginMaxFailures();
        this.janela = Duration.ofMinutes(props.loginLockMinutes());
        // expireAfterWrite: a janela conta a partir da última falha registrada
        this.falhas = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(janela).build();
        this.redis = "redis".equalsIgnoreCase(props.rateLimit().store()) ? redisProvider.getIfAvailable() : null;
    }

    public boolean bloqueado(String email) {
        if (redis != null) {
            try {
                String v = redis.opsForValue().get(chave(email));
                return v != null && Integer.parseInt(v) >= maxFalhas;
            } catch (RuntimeException e) {
                log.warn("Redis indisponível no controle de login; usando contador local: {}", e.getMessage());
            }
        }
        AtomicInteger n = falhas.getIfPresent(email);
        return n != null && n.get() >= maxFalhas;
    }

    public long segundosRestantes() {
        return janela.toSeconds();
    }

    public void registrarFalha(String email) {
        if (redis != null) {
            try {
                redis.opsForValue().increment(chave(email));
                redis.expire(chave(email), janela);      // renova a janela a cada falha
                return;
            } catch (RuntimeException e) {
                log.warn("Redis indisponível no controle de login; usando contador local: {}", e.getMessage());
            }
        }
        AtomicInteger n = falhas.get(email, k -> new AtomicInteger());
        n.incrementAndGet();
        falhas.put(email, n); // renova a expiração
    }

    public void limpar(String email) {
        if (redis != null) {
            try {
                redis.delete(chave(email));
            } catch (RuntimeException e) {
                log.warn("Redis indisponível no controle de login: {}", e.getMessage());
            }
        }
        falhas.invalidate(email);
    }

    private static String chave(String email) {
        return "loginfail:" + email;
    }
}
