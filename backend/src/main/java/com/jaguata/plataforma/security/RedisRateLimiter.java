package com.jaguata.plataforma.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Token bucket compartilhado entre réplicas, em Redis. O balde inteiro é atualizado por um script Lua (atômico) e
 * o relógio é o do próprio Redis, então instâncias com horas diferentes não distorcem o limite.
 * Se o Redis cair, cai-se para o limitador local (por instância): degrada a precisão, não a disponibilidade.
 */
public class RedisRateLimiter extends RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String LUA = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local cap = tonumber(ARGV[1])
            local periodMs = tonumber(ARGV[2])
            local d = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(d[1])
            local ts = tonumber(d[2])
            if tokens == nil then tokens = cap; ts = now end
            local per = periodMs / cap
            tokens = math.min(cap, tokens + (now - ts) / per)
            local allowed = 0
            local waitMs = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            else
              waitMs = math.ceil((1 - tokens) * per)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', KEYS[1], periodMs * 2)
            return {allowed, waitMs, math.floor(tokens)}
            """;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result tryConsume(String key, int capacity, Duration period) {
        try {
            List<Long> r = (List<Long>) redis.execute(SCRIPT, List.of("rl:" + key),
                    Integer.toString(capacity), Long.toString(period.toMillis()));
            if (r == null || r.size() < 3) return super.tryConsume(key, capacity, period);
            boolean ok = r.get(0) == 1L;
            long waitSeconds = ok ? 0 : Math.max(1, (r.get(1) + 999) / 1000);
            return new Result(ok, waitSeconds, ok ? r.get(2) : 0);
        } catch (RuntimeException e) {
            log.warn("Redis indisponível para o rate limit; usando limitador local: {}", e.toString(), e);
            return super.tryConsume(key, capacity, period);
        }
    }
}
