package com.jaguata.plataforma.exception;

/** Refresh token inexistente, expirado, revogado ou reutilizado. */
public class SessaoInvalidaException extends RuntimeException {
    public SessaoInvalidaException() {
        super("Sessão expirada. Entre novamente.");
    }
}
