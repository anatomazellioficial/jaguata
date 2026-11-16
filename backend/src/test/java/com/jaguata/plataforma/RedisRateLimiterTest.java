package com.jaguata.plataforma;

import com.jaguata.plataforma.security.RateLimiter;
import com.jaguata.plataforma.security.RedisRateLimiter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** O limite precisa ser COMPARTILHADO: duas "instâncias" da aplicação consomem o mesmo balde no Redis. */
@Testcontainers
class RedisRateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static JedisConnectionFactory fabrica;
    static StringRedisTemplate template;

    @BeforeAll
    static void conectar() {
        fabrica = new JedisConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), redis.getFirstMappedPort()));
        fabrica.afterPropertiesSet();
        template = new StringRedisTemplate(fabrica);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void fechar() {
        fabrica.destroy();
    }

    @Test
    void duasInstanciasCompartilhamOMesmoBalde() {
        RateLimiter a = new RedisRateLimiter(template);
        RateLimiter b = new RedisRateLimiter(template);
        String chave = "login:10.0.0.1";
        assertTrue(a.tryConsume(chave, 3, Duration.ofMinutes(1)).allowed());
        assertTrue(b.tryConsume(chave, 3, Duration.ofMinutes(1)).allowed());
        assertTrue(a.tryConsume(chave, 3, Duration.ofMinutes(1)).allowed());
        RateLimiter.Result negado = b.tryConsume(chave, 3, Duration.ofMinutes(1));
        assertFalse(negado.allowed(), "o 4º pedido, vindo da outra instância, estoura o limite comum");
        assertTrue(negado.retryAfterSeconds() >= 1);
    }

    @Test
    void chavesDiferentesTemBaldesDiferentes() {
        RateLimiter a = new RedisRateLimiter(template);
        assertTrue(a.tryConsume("k1", 1, Duration.ofMinutes(1)).allowed());
        assertFalse(a.tryConsume("k1", 1, Duration.ofMinutes(1)).allowed());
        assertTrue(a.tryConsume("k2", 1, Duration.ofMinutes(1)).allowed());
    }

    @Test
    void ReabasteceComOTempo() throws Exception {
        RateLimiter a = new RedisRateLimiter(template);
        assertTrue(a.tryConsume("rapido", 2, Duration.ofSeconds(1)).allowed());
        assertTrue(a.tryConsume("rapido", 2, Duration.ofSeconds(1)).allowed());
        assertFalse(a.tryConsume("rapido", 2, Duration.ofSeconds(1)).allowed());
        Thread.sleep(700);   // 2 fichas por segundo: ~1,4 ficha reabastecida
        assertTrue(a.tryConsume("rapido", 2, Duration.ofSeconds(1)).allowed());
    }

    @Test
    void redisForaDoArCaiParaOLimitadorLocal() {
        JedisConnectionFactory morta = new JedisConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", 1));   // ninguém escuta aqui
        morta.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(morta);
        t.afterPropertiesSet();
        RateLimiter r = new RedisRateLimiter(t);
        assertTrue(r.tryConsume("x", 1, Duration.ofMinutes(1)).allowed());
        assertFalse(r.tryConsume("x", 1, Duration.ofMinutes(1)).allowed(), "o fallback local ainda limita");
        morta.destroy();
    }
}
