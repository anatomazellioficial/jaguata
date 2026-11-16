package com.jaguata.plataforma.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** TOTP (RFC 6238): HMAC-SHA1, 6 dígitos, passo de 30 s. Implementação própria para não depender de biblioteca. */
public final class Totp {

    public static final int PASSO_SEGUNDOS = 30;
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** Código de 6 dígitos para o passo (contador) informado. */
    public static String codigo(byte[] segredo, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(segredo, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static long passoAtual(long epochSegundos) {
        return epochSegundos / PASSO_SEGUNDOS;
    }

    /**
     * Confere o código aceitando 1 passo de folga para cada lado (relógios desencontrados).
     *
     * @param ultimoPasso último passo já consumido: passos menores ou iguais são recusados (anti-replay)
     * @return o passo que casou, ou -1 se o código não vale
     */
    public static long verificar(byte[] segredo, String informado, long epochSegundos, Long ultimoPasso) {
        if (informado == null || !informado.matches("\\d{6}")) return -1;
        long atual = passoAtual(epochSegundos);
        long achado = -1;
        for (long p = atual - 1; p <= atual + 1; p++) {
            boolean igual = MessageDigest.isEqual(codigo(segredo, p).getBytes(StandardCharsets.US_ASCII),
                    informado.getBytes(StandardCharsets.US_ASCII));
            if (igual && (ultimoPasso == null || p > ultimoPasso)) achado = p;   // sem "break": tempo constante
        }
        return achado;
    }

    public static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }
}
