package com.jaguata.plataforma.exception;

/** Violação de regra de negócio (idade, cardápio, funil etc.). */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String message) {
        super(message);
    }
}
