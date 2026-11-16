package com.jaguata.plataforma.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jaguata.plataforma.conta.Termos;
import com.jaguata.plataforma.repository.UsuarioRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Confere se o usuário aceitou a versão VIGENTE dos Termos. Quando {@link Termos#VERSAO} muda, quem aceitou uma
 * versão anterior precisa aceitar de novo antes de continuar usando a API (veja {@link TermosFilter}).
 * O resultado fica em cache por poucos segundos; o aceite limpa o cache.
 */
@Component
public class TermosAceite {

    private final UsuarioRepository usuarios;
    private final Cache<UUID, Boolean> cache = Caffeine.newBuilder()
            .maximumSize(50_000).expireAfterWrite(Duration.ofSeconds(30)).build();

    public TermosAceite(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public boolean vigente(UUID usuarioId) {
        return cache.get(usuarioId, id -> usuarios.findById(id)
                .map(u -> Termos.VERSAO.equals(u.getTermosVersao()))
                .orElse(false));
    }

    public void invalidar(UUID usuarioId) {
        cache.invalidate(usuarioId);
    }
}
