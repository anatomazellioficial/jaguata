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

    /** Quanto tempo a marca de revogação fica no Redis: mais que a validade de qualquer access token. */
    private static final java.time.Duration TTL_REVOGACAO = java.time.Duration.ofHours(2);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SessaoAtiva.class);

    private final SessaoRepository sessoes;
    private final Cache<UUID, Boolean> cache;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;   // nulo com store=memory

    public SessaoAtiva(SessaoRepository sessoes, SecurityProperties props,
                       org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redisProvider) {
        this.sessoes = sessoes;
        this.redis = "redis".equalsIgnoreCase(props.rateLimit().store()) ? redisProvider.getIfAvailable() : null;
        this.cache = Caffeine.newBuilder()
                .maximumSize(50_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(0, props.sessaoCacheSegundos())))
                .build();
    }

    public boolean ativa(UUID familia) {
        // revogações ficam no Redis, compartilhadas: valem na hora em TODAS as réplicas (o cache local só guarda "ativa")
        if (redis != null) {
            try {
                if (Boolean.TRUE.equals(redis.hasKey(chave(familia)))) return false;
            } catch (RuntimeException e) {
                log.warn("Redis indisponível na checagem de sessão revogada: {}", e.getMessage());
            }
        }
        return cache.get(familia, f -> sessoes.existsByFamiliaAndRevogadaEmIsNullAndExpiraEmAfter(f, OffsetDateTime.now()));
    }

    public void invalidarTodas() {
        cache.invalidateAll();
    }

    /** Marca sessões como revogadas para as demais réplicas (no-op sem Redis). */
    public void revogar(java.util.Collection<UUID> familias) {
        if (redis == null) return;
        for (UUID f : familias) {
            try {
                redis.opsForValue().set(chave(f), "1", TTL_REVOGACAO);
            } catch (RuntimeException e) {
                log.warn("Redis indisponível ao registrar sessão revogada: {}", e.getMessage());
            }
        }
    }

    private static String chave(UUID familia) {
        return "sessao-revogada:" + familia;
    }
}
