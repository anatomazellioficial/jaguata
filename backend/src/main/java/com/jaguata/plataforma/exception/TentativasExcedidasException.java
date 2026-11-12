package com.jaguata.plataforma.exception;

/** Conta temporariamente bloqueada por excesso de falhas de login. */
public class TentativasExcedidasException extends RuntimeException {
    private final long retryAfterSeconds;

    public TentativasExcedidasException(long retryAfterSeconds) {
        super("Muitas tentativas de login. Tente novamente mais tarde.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
