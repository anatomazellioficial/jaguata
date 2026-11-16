package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.service.NotificacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara o resumo diário dos avisos (padrão: 08:00, horário de Brasília; mude com app.notificacoes.resumo-cron). */
@Component
public class ResumoDiarioAgendado {

    private static final Logger log = LoggerFactory.getLogger(ResumoDiarioAgendado.class);

    private final NotificacaoService notificacoes;

    public ResumoDiarioAgendado(NotificacaoService notificacoes) {
        this.notificacoes = notificacoes;
    }

    @Scheduled(cron = "${app.notificacoes.resumo-cron:0 0 8 * * *}", zone = "America/Sao_Paulo")
    public void enviar() {
        int n = notificacoes.enviarResumos();
        if (n > 0) log.info("Resumo diário enviado a {} pessoa(s).", n);
    }
}
