package com.jaguata.plataforma.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jaguata.plataforma.repository.SessaoRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Diz se a sessão (família do refresh token) a que um access token pertence continua ativa. É isto que faz
 * logout, troca de senha, "sair de todos os dispositivos" e exclusão da conta valerem NA HORA, em vez de
 * esperar o access token expirar.
 * <p>
 * Para não consultar o banco a cada requisição, o resultado fica em cache por poucos segundos. A instância que
 * revoga limpa o próprio cache; em outras réplicas a revogação vale em no máximo {@code sessao-cache-segundos}.
 */
@Component
public class SessaoAtiva {

    private final SessaoRepository sessoes;
    private final Cache<UUID, Boolean> cache;

    public SessaoAtiva(SessaoRepository sessoes, SecurityProperties props) {
        this.sessoes = sessoes;
        this.cache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(0, props.sessaoCacheSegundos())))
                .build();
    }

    public boolean ativa(UUID familia) {
        return cache.get(familia, f -> sessoes.existsByFamiliaAndRevogadaEmIsNullAndExpiraEmAfter(f, OffsetDateTime.now()));
    }

    public void invalidarTodas() {
        cache.invalidateAll();
    }
}
