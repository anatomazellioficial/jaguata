package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.NotificacaoPendente;
import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.email.EmailSender;
import com.jaguata.plataforma.email.EmailTemplates;
import com.jaguata.plataforma.email.EmailTemplates.Mensagem;
import com.jaguata.plataforma.repository.NotificacaoPendenteRepository;
import com.jaguata.plataforma.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Avisos por e-mail sobre o funil. Só vão a quem tem o e-mail verificado e não desligou as notificações (nem o
 * tipo de aviso), e <b>nunca citam nome de candidato</b> (dado pessoal): trazem só o título da vaga e quantidades.
 * Quem escolheu o <b>resumo diário</b> não recebe na hora: o evento entra numa fila e sai num único e-mail por dia.
 * O envio imediato acontece depois do commit, para um e-mail nunca anunciar algo que foi desfeito.
 */
@Service
public class NotificacaoService {

    /** Tipos de aviso. SUGESTOES tem preferência própria; os demais compartilham a preferência "andamento". */
    public enum Evento {
        SUGESTOES, ENVIADO, ENTREVISTA, CONTRATACAO
    }

    /** Consolidado de um dia, para o e-mail de resumo. */
    public record Resumo(int sugestoes, int enviados, int entrevistas, int contratacoes, Set<String> vagas) {
    }

    private final UsuarioRepository usuarios;
    private final NotificacaoPendenteRepository pendentes;
    private final EmailSender sender;
    private final EmailTemplates modelos;

    public NotificacaoService(UsuarioRepository usuarios, NotificacaoPendenteRepository pendentes, EmailSender sender,
                              EmailTemplates modelos) {
        this.usuarios = usuarios;
        this.pendentes = pendentes;
        this.sender = sender;
        this.modelos = modelos;
    }

    /** ONG: chegaram sugestões novas. */
    @Transactional
    public void novasSugestoes(UUID ongId, String tituloVaga, int quantidade) {
        notificar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId), Evento.SUGESTOES,
                tituloVaga, quantidade, modelos.novasSugestoes(tituloVaga, quantidade));
    }

    /** Empresa: a ONG enviou um candidato para a vaga. */
    @Transactional
    public void candidatoEnviado(UUID empresaId, String tituloVaga) {
        notificar(usuarios.findByEmpresaIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(empresaId), Evento.ENVIADO,
                tituloVaga, 1, modelos.candidatoEnviado(tituloVaga));
    }

    /** ONG: a empresa chamou um candidato para entrevista. */
    @Transactional
    public void convocacao(UUID ongId, String tituloVaga) {
        notificar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId), Evento.ENTREVISTA,
                tituloVaga, 1, modelos.convocacao(tituloVaga));
    }

    /** ONG: um candidato foi contratado. */
    @Transactional
    public void contratacao(UUID ongId, String tituloVaga) {
        notificar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId), Evento.CONTRATACAO,
                tituloVaga, 1, modelos.contratacao(tituloVaga));
    }

    private void notificar(List<Usuario> destinatarios, Evento evento, String vaga, int quantidade, Mensagem imediata) {
        List<String> agora = new ArrayList<>();
        for (Usuario u : destinatarios) {
            boolean tipoLigado = evento == Evento.SUGESTOES ? u.isNotifSugestoes() : u.isNotifAndamento();
            if (!tipoLigado) continue;
            if (u.isNotifResumoDiario()) {
                pendentes.save(new NotificacaoPendente(u.getId(), evento.name(), vaga, quantidade));   // mesma transação
            } else {
                agora.add(u.getEmail());
            }
        }
        if (agora.isEmpty()) return;
        Runnable envio = () -> agora.forEach(e -> sender.enviar(e, imediata.assunto(), imediata.corpo()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }

    /**
     * Envia o resumo diário (um e-mail por pessoa) e esvazia a fila. Chamado pelo agendador todo dia de manhã.
     *
     * @return quantos e-mails de resumo foram enviados
     */
    @Transactional
    public int enviarResumos() {
        List<NotificacaoPendente> todas = pendentes.findAllByOrderByUsuarioIdAscCriadaEmAsc();
        Map<UUID, List<NotificacaoPendente>> porUsuario = todas.stream()
                .collect(Collectors.groupingBy(NotificacaoPendente::getUsuarioId, LinkedHashMap::new, Collectors.toList()));
        int enviados = 0;
        for (Map.Entry<UUID, List<NotificacaoPendente>> e : porUsuario.entrySet()) {
            Usuario u = usuarios.findById(e.getKey()).orElse(null);
            if (u == null || !u.isNotificacoesEmail() || !u.isEmailVerificado()) continue;
            Mensagem m = modelos.resumoDiario(consolidar(e.getValue()));
            sender.enviar(u.getEmail(), m.assunto(), m.corpo());
            enviados++;
        }
        pendentes.deleteAll(todas);
        return enviados;
    }

    static Resumo consolidar(List<NotificacaoPendente> itens) {
        int sug = 0, env = 0, ent = 0, con = 0;
        Set<String> vagas = new LinkedHashSet<>();
        for (NotificacaoPendente p : itens) {
            switch (p.getTipo()) {
                case "SUGESTOES" -> sug += p.getQuantidade();
                case "ENVIADO" -> env += p.getQuantidade();
                case "ENTREVISTA" -> ent += p.getQuantidade();
                case "CONTRATACAO" -> con += p.getQuantidade();
                default -> {
                }
            }
            if (p.getTituloVaga() != null) vagas.add(p.getTituloVaga());
        }
        return new Resumo(sug, env, ent, con, vagas);
    }
}
