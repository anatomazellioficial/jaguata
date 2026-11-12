package com.jaguata.plataforma.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Limitador por token bucket em memória (por instância da aplicação).
 * Cada chave tem um balde que reabastece continuamente: capacidade/período.
 * O cache é limitado em tamanho e expira baldes ociosos, então um atacante não consegue
 * esgotar a memória variando a chave.
 * Para múltiplas réplicas, trocar por um armazenamento compartilhado (ex.: Redis).
 */
public class RateLimiter {

    public record Result(boolean allowed, long retryAfterSeconds, long remaining) {
    }

    private static final class Bucket {
        double tokens;
        long lastNanos;

        Bucket(double tokens, long lastNanos) {
            this.tokens = tokens;
            this.lastNanos = lastNanos;
        }
    }

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterAccess(Duration.ofHours(2))
            .build();
    private final LongSupplier nanoClock;

    public RateLimiter() {
        this(System::nanoTime);
    }

    /** Construtor para testes (relógio injetável). */
    public RateLimiter(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public Result tryConsume(String key, int capacity, Duration period) {
        Bucket b = buckets.get(key, k -> new Bucket(capacity, nanoClock.getAsLong()));
        double nanosPerToken = (double) period.toNanos() / capacity;
        synchronized (b) {
            long now = nanoClock.getAsLong();
            b.tokens = Math.min(capacity, b.tokens + (now - b.lastNanos) / nanosPerToken);
            b.lastNanos = now;
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return new Result(true, 0, (long) b.tokens);
            }
            long waitNanos = (long) ((1 - b.tokens) * nanosPerToken);
            return new Result(false, Math.max(1, Duration.ofNanos(waitNanos).toSeconds() + 1), 0);
        }
    }
}
