package com.jaguata.plataforma.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bloqueio temporário por conta após falhas seguidas de login (defesa contra força bruta
 * distribuída, que o limite por IP não pega). A chave é o e-mail normalizado, exista ele ou não,
 * para não revelar quais e-mails estão cadastrados.
 * Contrapartida conhecida: alguém pode bloquear a conta de outra pessoa por alguns minutos.
 */
@Component
public class LoginAttemptService {

    private final Cache<String, AtomicInteger> falhas;
    private final int maxFalhas;
    private final Duration janela;

    public LoginAttemptService(SecurityProperties props) {
        this.maxFalhas = props.loginMaxFailures();
        this.janela = Duration.ofMinutes(props.loginLockMinutes());
        // expireAfterWrite: a janela conta a partir da última falha registrada
        this.falhas = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(janela).build();
    }

    public boolean bloqueado(String email) {
        AtomicInteger n = falhas.getIfPresent(email);
        return n != null && n.get() >= maxFalhas;
    }

    public long segundosRestantes() {
        return janela.toSeconds();
    }

    public void registrarFalha(String email) {
        AtomicInteger n = falhas.get(email, k -> new AtomicInteger());
        n.incrementAndGet();
        falhas.put(email, n); // renova a expiração
    }

    public void limpar(String email) {
        falhas.invalidate(email);
    }
}
