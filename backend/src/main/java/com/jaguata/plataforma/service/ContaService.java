package com.jaguata.plataforma.service;

import com.jaguata.plataforma.conta.AuditoriaService;
import com.jaguata.plataforma.conta.SessaoService;
import com.jaguata.plataforma.conta.TokenContaService;
import com.jaguata.plataforma.conta.Termos;
import com.jaguata.plataforma.security.TermosAceite;
import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.domain.enums.PapelUsuario;
import com.jaguata.plataforma.domain.enums.TipoTokenConta;
import com.jaguata.plataforma.dto.Responses.ContaDadosResponse;
import com.jaguata.plataforma.email.EmailSender;
import com.jaguata.plataforma.email.EmailTemplates;
import com.jaguata.plataforma.email.EmailTemplates.Mensagem;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.EmpresaRepository;
import com.jaguata.plataforma.repository.OngRepository;
import com.jaguata.plataforma.repository.UsuarioRepository;
import com.jaguata.plataforma.security.LoginAttemptService;
import com.jaguata.plataforma.security.SecurityProperties;
import com.jaguata.plataforma.security.SenhaPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Ciclo de vida da conta: verificação de e-mail, recuperação e troca de senha, encerramento de sessões e
 * direitos do titular (exportar e excluir os próprios dados de acesso).
 * Princípios: respostas que dependem da existência de um e-mail são sempre idênticas (sem enumeração),
 * links são de uso único e curtos, e toda mudança de credencial encerra as sessões.
 */
@Service
public class ContaService {

    private static final Duration INTERVALO_REENVIO = Duration.ofSeconds(60);

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final OngRepository ongs;
    private final TokenContaService tokens;
    private final SessaoService sessoes;
    private final EmailSender emails;
    private final EmailTemplates modelos;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final LoginAttemptService tentativas;
    private final TermosAceite termosAceite;
    private final Duration validadeEmail;
    private final Duration validadeSenha;

    public ContaService(UsuarioRepository usuarios, EmpresaRepository empresas, OngRepository ongs,
                        TokenContaService tokens, SessaoService sessoes, EmailSender emails, EmailTemplates modelos,
                        AuditoriaService auditoria, PasswordEncoder encoder, LoginAttemptService tentativas,
                        SecurityProperties props, TermosAceite termosAceite) {
        this.termosAceite = termosAceite;
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.ongs = ongs;
        this.tokens = tokens;
        this.sessoes = sessoes;
        this.emails = emails;
        this.modelos = modelos;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.tentativas = tentativas;
        this.validadeEmail = Duration.ofHours(props.tokenEmailHoras());
        this.validadeSenha = Duration.ofMinutes(props.tokenSenhaMinutos());
    }

    // ------------------------------------------------------------------ verificação de e-mail
    public void enviarVerificacao(Usuario u) {
        String token = tokens.emitir(u.getId(), TipoTokenConta.VERIFICACAO_EMAIL, validadeEmail);
        Mensagem m = modelos.verificacaoEmail(token, validadeEmail.toHours());
        emails.enviar(u.getEmail(), m.assunto(), m.corpo());
    }

    @Transactional
    public void verificarEmail(String token) {
        UUID id = tokens.consumir(token, TipoTokenConta.VERIFICACAO_EMAIL);
        Usuario u = usuarios.findById(id).orElseThrow(com.jaguata.plataforma.exception.TokenInvalidoException::new);
        if (!u.isEmailVerificado()) {
            u.setEmailVerificado(true);
            u.setEmailVerificadoEm(OffsetDateTime.now());
            auditoria.registrar(u.getId(), u.getPapel().name(), "EMAIL_VERIFICADO", "usuario", u.getId(), null);
        }
    }

    /** Resposta sempre igual, exista ou não o e-mail; reenvio no máximo 1 por minuto por conta. */
    public void reenviarVerificacao(String email) {
        usuarios.findByEmail(normalizar(email))
                .filter(u -> !u.isEmailVerificado())
                .filter(u -> !tokens.emitidoRecentemente(u.getId(), TipoTokenConta.VERIFICACAO_EMAIL, INTERVALO_REENVIO))
                .ifPresent(this::enviarVerificacao);
    }

    // ------------------------------------------------------------------ recuperação de senha
    /** Resposta sempre igual. Conta ainda não verificada recebe o link de verificação no lugar. */
    public void esqueciSenha(String email) {
        usuarios.findByEmail(normalizar(email)).ifPresent(u -> {
            if (!u.isEmailVerificado()) {
                if (!tokens.emitidoRecentemente(u.getId(), TipoTokenConta.VERIFICACAO_EMAIL, INTERVALO_REENVIO)) enviarVerificacao(u);
                return;
            }
            if (tokens.emitidoRecentemente(u.getId(), TipoTokenConta.REDEFINICAO_SENHA, INTERVALO_REENVIO)) return;
            String token = tokens.emitir(u.getId(), TipoTokenConta.REDEFINICAO_SENHA, validadeSenha);
            Mensagem m = modelos.redefinicaoSenha(token, validadeSenha.toMinutes());
            emails.enviar(u.getEmail(), m.assunto(), m.corpo());
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        UUID id = tokens.validar(token, TipoTokenConta.REDEFINICAO_SENHA);   // ainda não gasta o link
        Usuario u = usuarios.findById(id).orElseThrow(com.jaguata.plataforma.exception.TokenInvalidoException::new);
        SenhaPolicy.validar(novaSenha, u.getEmail());                         // senha fraca não queima o link
        tokens.consumir(token, TipoTokenConta.REDEFINICAO_SENHA);
        trocarSenha(u, novaSenha);
        if (!u.isEmailVerificado()) {   // quem recebeu o link no e-mail provou o controle dele
            u.setEmailVerificado(true);
            u.setEmailVerificadoEm(OffsetDateTime.now());
        }
        auditoria.registrar(u.getId(), u.getPapel().name(), "SENHA_REDEFINIDA", "usuario", u.getId(), null);
        avisarSenhaAlterada(u);
    }

    @Transactional
    public void alterarSenha(UUID usuarioId, String senhaAtual, String novaSenha) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        if (!encoder.matches(senhaAtual, u.getSenhaHash())) throw new RegraNegocioException("Senha atual incorreta.");
        SenhaPolicy.validar(novaSenha, u.getEmail());
        if (encoder.matches(novaSenha, u.getSenhaHash())) throw new RegraNegocioException("A nova senha deve ser diferente da atual.");
        trocarSenha(u, novaSenha);
        auditoria.registrar(u.getId(), u.getPapel().name(), "SENHA_ALTERADA", "usuario", u.getId(), null);
        avisarSenhaAlterada(u);
    }

    private void trocarSenha(Usuario u, String nova) {
        u.setSenhaHash(encoder.encode(nova));
        u.setSenhaAlteradaEm(OffsetDateTime.now());
        sessoes.encerrarTodas(u.getId());       // toda mudança de credencial derruba as sessões
        tentativas.limpar(u.getEmail());
    }

    private void avisarSenhaAlterada(Usuario u) {
        Mensagem m = modelos.senhaAlterada();
        emails.enviar(u.getEmail(), m.assunto(), m.corpo());
    }

    // ------------------------------------------------------------------ sessões
    @Transactional
    public void sairDeTodosOsDispositivos(UUID usuarioId) {
        sessoes.encerrarTodas(usuarioId);
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        auditoria.registrar(usuarioId, u.getPapel().name(), "SESSOES_ENCERRADAS", "usuario", usuarioId, null);
    }

    // ------------------------------------------------------------------ termos e notificações
    /** Registra o aceite da versão vigente dos Termos (quem aceitou uma versão anterior fica bloqueado até aceitar). */
    @Transactional
    public void aceitarTermos(UUID usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        u.setTermosVersao(Termos.VERSAO);
        u.setTermosAceitosEm(OffsetDateTime.now());
        auditoria.registrar(usuarioId, u.getPapel().name(), "TERMOS_ACEITOS", "usuario", usuarioId, "versao=" + Termos.VERSAO);
        termosAceite.invalidar(usuarioId);
    }

    @Transactional
    public void notificacoes(UUID usuarioId, boolean ativas) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        u.setNotificacoesEmail(ativas);
        auditoria.registrar(usuarioId, u.getPapel().name(), "NOTIFICACOES_" + (ativas ? "LIGADAS" : "DESLIGADAS"),
                "usuario", usuarioId, null);
    }

    // ------------------------------------------------------------------ direitos do titular
    /** Cópia dos dados de acesso da conta (LGPD, art. 18: confirmação e acesso). */
    @Transactional(readOnly = true)
    public ContaDadosResponse dados(UUID usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        Empresa e = u.getEmpresaId() == null ? null : empresas.findById(u.getEmpresaId()).orElse(null);
        Ong o = u.getOngId() == null ? null : ongs.findById(u.getOngId()).orElse(null);
        return ContaDadosResponse.de(u, e, o, sessoes.ativas(usuarioId));
    }

    /**
     * Exclui o acesso (login, tokens e sessões). Os dados cadastrais da empresa/ONG, que são de pessoa jurídica
     * e públicos, permanecem. Administradores não se excluem por aqui.
     */
    @Transactional
    public void excluirConta(UUID usuarioId, String senha) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuarioId));
        if (u.getPapel() == PapelUsuario.ADMIN) throw new RegraNegocioException("Contas de administrador não podem ser excluídas por aqui.");
        if (!encoder.matches(senha, u.getSenhaHash())) throw new RegraNegocioException("Senha incorreta.");
        auditoria.registrar(usuarioId, u.getPapel().name(), "CONTA_EXCLUIDA", "usuario", usuarioId, null);
        Mensagem m = modelos.contaExcluida();
        emails.enviar(u.getEmail(), m.assunto(), m.corpo());
        sessoes.encerrarTodas(usuarioId);
        usuarios.delete(u);                       // tokens e sessões saem em cascata (FK ON DELETE CASCADE)
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
