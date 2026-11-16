package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.repository.SessaoRepository;
import com.jaguata.plataforma.repository.TokenContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Apaga tokens e sessões expirados há mais de 7 dias (guardamos um pouco para investigação de incidentes). */
@Component
public class LimpezaAgendada {

    private static final Logger log = LoggerFactory.getLogger(LimpezaAgendada.class);

    private final TokenContaRepository tokens;
    private final SessaoRepository sessoes;

    public LimpezaAgendada(TokenContaRepository tokens, SessaoRepository sessoes) {
        this.tokens = tokens;
        this.sessoes = sessoes;
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void limpar() {
        OffsetDateTime limite = OffsetDateTime.now().minusDays(7);
        int t = tokens.apagarExpirados(limite);
        int s = sessoes.apagarExpiradas(limite);
        if (t + s > 0) log.info("Limpeza: {} tokens e {} sessões expirados removidos.", t, s);
    }
}
