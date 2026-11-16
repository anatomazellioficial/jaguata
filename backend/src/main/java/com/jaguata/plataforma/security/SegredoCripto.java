package com.jaguata.plataforma.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra os segredos TOTP em repouso (AES-256-GCM): um dump do banco, sozinho, não entrega os segundos fatores.
 * A chave vem de TOTP_KEY ou, se ausente, é derivada de JWT_SECRET (trocar o segredo invalida os 2FA cadastrados).
 */
@Component
public class SegredoCripto {

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public SegredoCripto(SecurityProperties props, @Value("${app.jwt.secret:}") String jwtSecret) {
        String base = props.totpChave() == null || props.totpChave().isBlank()
                ? "jaguata-totp|" + jwtSecret : props.totpChave();
        try {
            this.chave = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(base.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String cifrar(byte[] claro) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(claro);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] decifrar(String cifrado) {
        try {
            byte[] in = Base64.getDecoder().decode(cifrado);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, in, 0, 12));
            return c.doFinal(in, 12, in.length - 12);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Segredo TOTP ilegível (chave alterada?).", e);
        }
    }
}
