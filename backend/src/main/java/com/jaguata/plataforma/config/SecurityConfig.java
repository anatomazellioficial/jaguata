package com.jaguata.plataforma.config;

import com.jaguata.plataforma.security.RateLimitFilter;
import com.jaguata.plataforma.security.RateLimiter;
import com.jaguata.plataforma.security.RedisRateLimiter;
import com.jaguata.plataforma.security.RequestSizeFilter;
import com.jaguata.plataforma.security.SessaoAtiva;
import com.jaguata.plataforma.security.TermosAceite;
import com.jaguata.plataforma.security.TermosFilter;
import com.jaguata.plataforma.security.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * API stateless: tudo exige JWT (HS256, com emissor validado) exceto /api/auth/**.
 * A autorização por posse do recurso é feita pelos @PreAuthorize + {@link com.jaguata.plataforma.security.AccessPolicy}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Em memória (por instância) ou em Redis (compartilhado entre réplicas): app.security.rate-limit.store. */
    /** Relógio injetável (os testes de TOTP controlam o tempo). */
    @Bean
    java.time.Clock relogio() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    RateLimiter rateLimiter(SecurityProperties props, ObjectProvider<StringRedisTemplate> redis) {
        if ("redis".equalsIgnoreCase(props.rateLimit().store())) {
            StringRedisTemplate t = redis.getIfAvailable();
            if (t != null) return new RedisRateLimiter(t);
        }
        return new RateLimiter();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter conversor,
                                    SecurityProperties props, RateLimiter limiter,
                                    TermosAceite termosAceite) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)     // sem cookies de sessão: Bearer token no header
                .cors(AbstractHttpConfigurer::disable)     // sem CORS: o navegador só fala com o BFF (mesma origem)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        // é uma API JSON: nada deve ser renderizado, embutido ou carregado a partir dela
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .permissionsPolicy(p -> p.policy("geolocation=(), camera=(), microphone=()")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/consulta/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conversor)))
                .addFilterBefore(new RequestSizeFilter(props.maxBodyBytes()), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new RateLimitFilter(limiter, props.rateLimit()), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new TermosFilter(termosAceite), BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    SecretKey jwtKey(@Value("${app.jwt.secret:}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) é obrigatório e deve ter no mínimo 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /** Algoritmo fixado em HS256 (recusa "none" e confusão de algoritmos) e emissor obrigatório. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey, SecurityProperties props, SessaoAtiva sessaoAtiva) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        // além de assinatura, emissor e validade: a sessão do token (claim "sid") precisa continuar ativa
        OAuth2TokenValidator<Jwt> sessao = jwt -> {
            try {
                String sid = jwt.getClaimAsString("sid");
                if (sid != null && sessaoAtiva.ativa(java.util.UUID.fromString(sid))) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (IllegalArgumentException ignorado) {
                // sid malformado: cai na recusa abaixo
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sessão encerrada.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.jwtIssuer()), sessao));
        return decoder;
    }

    /** Claim "papel" -> authority ROLE_<PAPEL>. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("papel");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(authorities);
        return conversor;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
