package com.jaguata.plataforma.conta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração de tokens aleatórios e hash SHA-256: só o hash vai para o banco. */
public final class Hashes {
    private Hashes() {
    }

    private static final SecureRandom RND = new SecureRandom();

    /** 256 bits aleatórios em base64url (43 caracteres). */
    public static String tokenAleatorio() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
