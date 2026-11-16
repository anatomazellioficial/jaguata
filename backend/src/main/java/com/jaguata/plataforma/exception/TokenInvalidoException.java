package com.jaguata.plataforma.exception;

/** Link/token de conta inexistente, expirado, já usado ou de outro tipo (mensagem única, para não revelar qual). */
public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException() {
        super("Link inválido ou expirado. Solicite um novo.");
    }
}
