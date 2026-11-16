package com.jaguata.plataforma.exception;

/** Senha correta, mas a conta tem segundo fator e o código não foi enviado. */
public class TotpRequeridoException extends RuntimeException {
    public TotpRequeridoException() {
        super("Informe o código de 6 dígitos do seu app autenticador.");
    }
}
