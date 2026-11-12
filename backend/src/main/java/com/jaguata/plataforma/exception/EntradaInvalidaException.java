package com.jaguata.plataforma.exception;

/** Parâmetro mal formado (ex.: CNPJ com dígito verificador errado, CEP com tamanho errado). */
public class EntradaInvalidaException extends RuntimeException {
    public EntradaInvalidaException(String message) {
        super(message);
    }
}
