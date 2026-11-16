package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.UsuarioRepository;
import com.jaguata.plataforma.security.SegredoCripto;
import com.jaguata.plataforma.security.Totp;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Segundo fator TOTP (apps como Google Authenticator, Authy, 1Password). */
@Service
public class TotpService {

    public record Configuracao(String segredo, String otpauthUri) {
    }

    private static final String EMISSOR = "Jaguatá";

    private final UsuarioRepository usuarios;
    private final SegredoCripto cripto;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final SecureRandom random = new SecureRandom();
    private final java.time.Clock relogio;

    public TotpService(UsuarioRepository usuarios, SegredoCripto cripto, PasswordEncoder encoder,
                       AuditoriaService auditoria, java.time.Clock relogio) {
        this.relogio = relogio;
        this.usuarios = usuarios;
        this.cripto = cripto;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public boolean ativo(UUID usuarioId) {
        return usuario(usuarioId).isTotpAtivo();
    }

    /** Gera um segredo novo (ainda inativo) e devolve o que o app autenticador precisa. Pode ser repetido até ativar. */
    @Transactional
    public Configuracao iniciar(UUID usuarioId) {
        Usuario u = usuario(usuarioId);
        if (u.isTotpAtivo()) throw new RegraNegocioException("O segundo fator já está ativo.");
        byte[] segredo = new byte[20];
        random.nextBytes(segredo);
        u.setTotpSegredo(cripto.cifrar(segredo));
        u.setTotpUltimoPasso(null);
        String b32 = Totp.base32(segredo);
        String rotulo = enc(EMISSOR) + ":" + enc(u.getEmail());
        return new Configuracao(b32, "otpauth://totp/" + rotulo + "?secret=" + b32 + "&issuer=" + enc(EMISSOR)
                + "&algorithm=SHA1&digits=6&period=" + Totp.PASSO_SEGUNDOS);
    }

    /** Confirma que o app foi configurado corretamente (código válido) e liga o segundo fator. */
    @Transactional
    public void ativar(UUID usuarioId, String codigo) {
        Usuario u = usuario(usuarioId);
        if (u.isTotpAtivo()) throw new RegraNegocioException("O segundo fator já está ativo.");
        if (u.getTotpSegredo() == null) throw new RegraNegocioException("Gere o segredo antes de ativar.");
        long passo = Totp.verificar(cripto.decifrar(u.getTotpSegredo()), codigo, relogio.instant().getEpochSecond(), null);
        if (passo < 0) throw new RegraNegocioException("Código inválido. Confira o app e tente de novo.");
        u.setTotpAtivo(true);
        u.setTotpUltimoPasso(passo);
        auditoria.registrar(u.getId(), u.getPapel().name(), "TOTP_ATIVADO", "usuario", u.getId(), null);
    }

    @Transactional
    public void desativar(UUID usuarioId, String senha, String codigo) {
        Usuario u = usuario(usuarioId);
        if (!u.isTotpAtivo()) throw new RegraNegocioException("O segundo fator não está ativo.");
        boolean senhaOk = encoder.matches(senha, u.getSenhaHash());
        boolean codigoOk = confirmar(u, codigo);
        if (!senhaOk || !codigoOk) throw new RegraNegocioException("Senha ou código incorretos.");
        u.setTotpAtivo(false);
        u.setTotpSegredo(null);
        u.setTotpUltimoPasso(null);
        auditoria.registrar(u.getId(), u.getPapel().name(), "TOTP_DESATIVADO", "usuario", u.getId(), null);
    }

    /**
     * Valida o código do login e consome o passo (o mesmo código não vale duas vezes).
     * Deve rodar dentro de uma transação, para persistir o passo usado.
     */
    public boolean confirmar(Usuario u, String codigo) {
        if (u.getTotpSegredo() == null) return false;
        long passo = Totp.verificar(cripto.decifrar(u.getTotpSegredo()), codigo, relogio.instant().getEpochSecond(),
                u.getTotpUltimoPasso());
        if (passo < 0) return false;
        u.setTotpUltimoPasso(passo);
        return true;
    }

    private Usuario usuario(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", id));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
