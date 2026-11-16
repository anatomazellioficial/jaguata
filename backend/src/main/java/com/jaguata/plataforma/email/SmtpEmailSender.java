package com.jaguata.plataforma.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envio por SMTP, de forma assíncrona: a resposta HTTP não espera o servidor de e-mail (o que também
 * impede deduzir se um e-mail existe pelo tempo de resposta).
 */
@Component
@ConditionalOnProperty(name = "app.mail.modo", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final ObjectProvider<JavaMailSender> mail;
    private final String de;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mail,
                           @Value("${app.mail.from:Jaguatá <nao-responda@jaguata.local>}") String de) {
        this.mail = mail;
        this.de = de;
    }

    @Override
    @Async
    public void enviar(String para, String assunto, String corpo) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.error("E-mail NÃO enviado: SMTP não configurado (defina spring.mail.host / SPRING_MAIL_HOST).");
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(de);
            m.setTo(para);
            m.setSubject(assunto);
            m.setText(corpo);
            sender.send(m);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
