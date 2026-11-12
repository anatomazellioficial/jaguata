package com.jaguata.plataforma.exception;

/** CNPJ ou CEP inexistente nas bases consultadas. */
public class ConsultaNaoEncontradaException extends RuntimeException {
    public ConsultaNaoEncontradaException(String message) {
        super(message);
    }
}
