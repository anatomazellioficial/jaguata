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
        @DefaultValue("30") int tokenSenhaMinutos,
        /**
         * Tolerância para duas renovações simultâneas do mesmo refresh token (várias instâncias do BFF ou abas):
         * dentro desta janela, reapresentar um token já trocado NÃO derruba a família. 0 = reuso sempre derruba.
         */
        @DefaultValue("10") int refreshGraceSegundos,
        /** Por quanto tempo uma instância guarda "esta sessão está ativa" (a revogação vale na hora na instância que revogou). */
        @DefaultValue("5") int sessaoCacheSegundos,
        /** Chave (texto) para cifrar os segredos TOTP em repouso. Vazia = derivada de JWT_SECRET. */
        @DefaultValue("") String totpChave) {

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
            @DefaultValue("0") int trustedProxyHops,
            /** Onde ficam os contadores: "memory" (por instância) ou "redis" (compartilhado entre réplicas). */
            @DefaultValue("memory") String store) {
    }
}
