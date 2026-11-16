package com.jaguata.plataforma;

import com.jaguata.plataforma.repository.SessaoRepository;
import com.jaguata.plataforma.security.SecurityProperties;
import com.jaguata.plataforma.security.SessaoAtiva;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Com Redis, uma revogação feita numa réplica vale NA HORA nas outras, mesmo que elas tenham guardado "ativa" no
 * cache local (que, sem Redis, só se renovaria em alguns segundos).
 */
@Testcontainers
class SessaoAtivaRedisTest {

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

    private static SecurityProperties props(String store) {
        return new SecurityProperties(
                new SecurityProperties.Limites(true, 10, 5, 120, 30, 10, 0, store),
                5, 15, 65536, "jaguata", 30, 24, 30, 10, 60 /* cache longo: só o Redis pode derrubar */, "");
    }

    private static SessaoAtiva replica(SessaoRepository banco, String store) {
        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("redis", template);
        return new SessaoAtiva(banco, props(store), bf.getBeanProvider(StringRedisTemplate.class));
    }

    @Test
    void revogacaoNaReplicaAValeNaReplicaB() {
        SessaoRepository banco = Mockito.mock(SessaoRepository.class);
        Mockito.when(banco.existsByFamiliaAndRevogadaEmIsNullAndExpiraEmAfter(Mockito.any(), Mockito.any(OffsetDateTime.class)))
                .thenReturn(true);   // o banco ainda diz "ativa" (a revogação no banco é da réplica que a fez)
        SessaoAtiva a = replica(banco, "redis");
        SessaoAtiva b = replica(banco, "redis");
        UUID familia = UUID.randomUUID();

        assertTrue(a.ativa(familia));
        assertTrue(b.ativa(familia), "B guardou 'ativa' no cache local");

        a.revogar(List.of(familia));

        assertFalse(a.ativa(familia));
        assertFalse(b.ativa(familia), "mesmo com cache local, B enxerga a revogação pelo Redis");
        assertTrue(b.ativa(UUID.randomUUID()), "outras sessões não são afetadas");
    }

    @Test
    void semRedisARevogacaoDependeDoCacheLocal() {
        SessaoRepository banco = Mockito.mock(SessaoRepository.class);
        Mockito.when(banco.existsByFamiliaAndRevogadaEmIsNullAndExpiraEmAfter(Mockito.any(), Mockito.any(OffsetDateTime.class)))
                .thenReturn(true);
        SessaoAtiva b = replica(banco, "memory");
        UUID familia = UUID.randomUUID();
        assertTrue(b.ativa(familia));
        b.revogar(List.of(familia));                       // no-op: sem Redis não há canal entre réplicas
        assertTrue(b.ativa(familia), "limitação conhecida da instalação sem Redis (até o cache expirar)");
        b.invalidarTodas();
    }
}
