package com.jaguata.plataforma.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Só para desenvolvimento (app.mail.modo=log): escreve o e-mail no log, inclusive links com token. NÃO usar em produção. */
@Component
@ConditionalOnProperty(name = "app.mail.modo", havingValue = "log")
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void enviar(String para, String assunto, String corpo) {
        log.warn("[E-MAIL DE DESENVOLVIMENTO] para={} assunto={}\n{}", para, assunto, corpo);
    }
}
