package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.email.EmailSender;
import com.jaguata.plataforma.email.EmailTemplates;
import com.jaguata.plataforma.email.EmailTemplates.Mensagem;
import com.jaguata.plataforma.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Avisos por e-mail sobre o funil. Só vão a quem tem o e-mail verificado e não desligou as notificações, e
 * <b>nunca citam nome de candidato</b> (dado pessoal): trazem só o título da vaga e quantidades. O envio acontece
 * depois do commit, para um e-mail nunca anunciar algo que foi desfeito.
 */
@Service
public class NotificacaoService {

    private final UsuarioRepository usuarios;
    private final EmailSender sender;
    private final EmailTemplates modelos;

    public NotificacaoService(UsuarioRepository usuarios, EmailSender sender, EmailTemplates modelos) {
        this.usuarios = usuarios;
        this.sender = sender;
        this.modelos = modelos;
    }

    /** ONG: chegaram sugestões novas. */
    @Transactional(readOnly = true)
    public void novasSugestoes(UUID ongId, String tituloVaga, int quantidade) {
        enviar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId),
                modelos.novasSugestoes(tituloVaga, quantidade));
    }

    /** Empresa: a ONG enviou um candidato para a vaga. */
    @Transactional(readOnly = true)
    public void candidatoEnviado(UUID empresaId, String tituloVaga) {
        enviar(usuarios.findByEmpresaIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(empresaId),
                modelos.candidatoEnviado(tituloVaga));
    }

    /** ONG: a empresa chamou um candidato para entrevista. */
    @Transactional(readOnly = true)
    public void convocacao(UUID ongId, String tituloVaga) {
        enviar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId),
                modelos.convocacao(tituloVaga));
    }

    /** ONG: um candidato foi contratado. */
    @Transactional(readOnly = true)
    public void contratacao(UUID ongId, String tituloVaga) {
        enviar(usuarios.findByOngIdAndEmailVerificadoTrueAndNotificacoesEmailTrue(ongId),
                modelos.contratacao(tituloVaga));
    }

    private void enviar(List<Usuario> destinatarios, Mensagem m) {
        if (destinatarios.isEmpty()) return;
        List<String> emails = destinatarios.stream().map(Usuario::getEmail).toList();
        Runnable envio = () -> emails.forEach(e -> sender.enviar(e, m.assunto(), m.corpo()));
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
}
