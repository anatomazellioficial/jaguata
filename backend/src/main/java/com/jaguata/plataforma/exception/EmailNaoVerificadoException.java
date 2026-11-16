package com.jaguata.plataforma.exception;

/** Senha correta, mas o e-mail ainda não foi confirmado. */
public class EmailNaoVerificadoException extends RuntimeException {
    public EmailNaoVerificadoException() {
        super("Confirme o seu e-mail para entrar. Enviamos um link de verificação.");
    }
}
