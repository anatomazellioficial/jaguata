package com.jaguata.plataforma.security;

import com.jaguata.plataforma.exception.RegraNegocioException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Política de senhas (alinhada ao NIST 800-63B: tamanho e lista de senhas comuns, sem regras de composição).
 * Mínimo de 10 caracteres; no máximo 72 bytes (limite do BCrypt, que truncaria em silêncio).
 */
public final class SenhaPolicy {
    private SenhaPolicy() {
    }

    public static final int MIN = 10;

    private static final Set<String> COMUNS = Set.of(
            "1234567890", "12345678901", "123456789012", "0123456789", "qwertyuiop", "qwerty12345",
            "password", "password1", "password12", "password123", "password1234", "passw0rd123",
            "senha12345", "senha123456", "senhasenha", "mudar12345", "mudar123456", "admin12345",
            "administrador", "abcdefghij", "abc1234567", "iloveyou12", "letmein123", "welcome123",
            "brasil1234", "brasil12345", "jaguata123", "jaguata1234", "jaguatajaguata");

    public static void validar(String senha, String email) {
        if (senha == null || senha.length() < MIN) {
            throw new RegraNegocioException("A senha deve ter no mínimo " + MIN + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new RegraNegocioException("A senha é longa demais (máximo de 72 bytes).");
        }
        String s = senha.toLowerCase(Locale.ROOT);
        if (COMUNS.contains(s) || senha.chars().distinct().count() < 4) {
            throw new RegraNegocioException("Senha muito comum ou previsível. Escolha outra.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && s.contains(local)) {
                throw new RegraNegocioException("A senha não pode conter o seu e-mail.");
            }
        }
    }
}
