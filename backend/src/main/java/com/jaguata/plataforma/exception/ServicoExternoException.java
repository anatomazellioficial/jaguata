package com.jaguata.plataforma.exception;

/** Serviço externo (Receita/CEP/geocodificação) indisponível, lento ou com resposta inesperada. */
public class ServicoExternoException extends RuntimeException {
    public ServicoExternoException(String message, Throwable cause) {
        super(message, cause);
    }

    public ServicoExternoException(String message) {
        super(message);
    }
}
