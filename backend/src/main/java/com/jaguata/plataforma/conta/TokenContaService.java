package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.domain.TokenConta;
import com.jaguata.plataforma.domain.enums.TipoTokenConta;
import com.jaguata.plataforma.exception.TokenInvalidoException;
import com.jaguata.plataforma.repository.TokenContaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tokens de uso único para verificação de e-mail e redefinição de senha. */
@Service
public class TokenContaService {

    private final TokenContaRepository tokens;

    public TokenContaService(TokenContaRepository tokens) {
        this.tokens = tokens;
    }

    /**
     * Emite um token novo (256 bits aleatórios) e invalida os pendentes do mesmo tipo: só o último link vale.
     * Devolve o token em claro, que vai apenas no e-mail; no banco fica só o hash.
     */
    @Transactional
    public String emitir(UUID usuarioId, TipoTokenConta tipo, Duration validade) {
        OffsetDateTime agora = OffsetDateTime.now();
        tokens.invalidarPendentes(usuarioId, tipo, agora);
        String bruto = Hashes.tokenAleatorio();
        TokenConta t = new TokenConta();
        t.setUsuarioId(usuarioId);
        t.setTipo(tipo);
        t.setTokenHash(Hashes.sha256(bruto));
        t.setExpiraEm(agora.plus(validade));
        tokens.save(t);
        return bruto;
    }

    /** True se já foi emitido um token deste tipo no intervalo (para limitar reenvios). */
    @Transactional(readOnly = true)
    public boolean emitidoRecentemente(UUID usuarioId, TipoTokenConta tipo, Duration janela) {
        return tokens.existsByUsuarioIdAndTipoAndCriadoEmAfter(usuarioId, tipo, OffsetDateTime.now().minus(janela));
    }

    /**
     * Consome o token (uso único). Falha com a MESMA mensagem para inexistente, expirado, usado ou de outro tipo.
     * @return id do usuário dono do token
     */
    @Transactional
    public UUID consumir(String bruto, TipoTokenConta tipo) {
        TokenConta t = localizarValido(bruto, tipo);
        t.setUsadoEm(OffsetDateTime.now());
        return t.getUsuarioId();
    }

    /** Confere se o token é válido SEM consumi-lo (ex.: validar a nova senha antes de gastar o link). */
    @Transactional(readOnly = true)
    public UUID validar(String bruto, TipoTokenConta tipo) {
        return localizarValido(bruto, tipo).getUsuarioId();
    }

    private TokenConta localizarValido(String bruto, TipoTokenConta tipo) {
        if (bruto == null || bruto.isBlank() || bruto.length() > 128) throw new TokenInvalidoException();
        TokenConta t = tokens.findByTokenHash(Hashes.sha256(bruto)).orElseThrow(TokenInvalidoException::new);
        if (t.getTipo() != tipo || t.getUsadoEm() != null || t.getExpiraEm().isBefore(OffsetDateTime.now())) {
            throw new TokenInvalidoException();
        }
        return t;
    }
}
