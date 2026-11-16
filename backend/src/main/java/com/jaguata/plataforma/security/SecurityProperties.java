package com.jaguata.plataforma.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Parâmetros de segurança (prefixo app.security.*). */
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @DefaultValue Limites rateLimit,
        @DefaultValue("5") int loginMaxFailures,
        @DefaultValue("15") int loginLockMinutes,
        @DefaultValue("65536") int maxBodyBytes,
        @DefaultValue("jaguata") String jwtIssuer,
        /** Validade do refresh token (a sessão "lembrada"). O access token é curto (app.jwt.ttl). */
        @DefaultValue("30") int refreshDias,
        @DefaultValue("24") int tokenEmailHoras,
        @DefaultValue("30") int tokenSenhaMinutos) {

    public record Limites(
            @DefaultValue("true") boolean enabled,
            /** Tentativas de login por minuto, por IP. */
            @DefaultValue("10") int loginPerMinute,
            /** Cadastros por hora, por IP. */
            @DefaultValue("5") int registroPerHour,
            /** Requisições por minuto, por usuário autenticado (ou IP). */
            @DefaultValue("120") int apiPerMinute,
            /** Consultas de CNPJ/CEP por minuto: por usuário autenticado ou, sem token, por IP. */
            @DefaultValue("30") int consultaPerMinute,
            /** Operações sensíveis de conta (verificar/reenviar e-mail, esqueci/redefinir senha) por hora, por IP. */
            @DefaultValue("10") int contaPerHour,
            /**
             * Quantos proxies confiáveis existem na frente da aplicação. 0 = ignora X-Forwarded-For
             * (único valor seguro se o backend for acessível diretamente).
             */
            @DefaultValue("0") int trustedProxyHops) {
    }
}
