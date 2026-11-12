package com.jaguata.plataforma.integracao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuração das integrações externas (prefixo app.integracoes.*). */
@ConfigurationProperties("app.integracoes")
public record IntegracoesProperties(
        @DefaultValue("https://brasilapi.com.br") String brasilApiUrl,
        @DefaultValue("https://nominatim.openstreetmap.org") String nominatimUrl,
        /** Identifica a aplicação para o Nominatim (política de uso exige User-Agent). Não inclua dados pessoais. */
        @DefaultValue("jaguata-plataforma/0.1") String userAgent,
        @DefaultValue("3000") int connectTimeoutMs,
        @DefaultValue("6000") int readTimeoutMs,
        /** Intervalo mínimo entre chamadas ao Nominatim (a política pública permite 1 req/s). */
        @DefaultValue("1100") int nominatimMinIntervalMs,
        @DefaultValue("24") int cnpjCacheHours,
        @DefaultValue("30") int cepCacheDays) {
}
