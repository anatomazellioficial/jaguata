package com.jaguata.plataforma.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Envio de e-mail assíncrono e limpeza periódica de tokens/sessões expirados. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
