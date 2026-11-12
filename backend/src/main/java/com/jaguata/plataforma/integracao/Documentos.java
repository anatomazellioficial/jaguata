package com.jaguata.plataforma.integracao;

import com.jaguata.plataforma.exception.EntradaInvalidaException;

/** Normalização e validação de CNPJ e CEP (antes de qualquer chamada externa). */
public final class Documentos {
    private Documentos() {
    }

    private static final int[] PESOS_12 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_13 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** Mantém só dígitos e valida tamanho + dígitos verificadores. */
    public static String cnpj(String bruto) {
        String c = soDigitos(bruto);
        if (c.length() != 14 || c.chars().distinct().count() == 1
                || dv(c, 12, PESOS_12) != c.charAt(12) - '0' || dv(c, 13, PESOS_13) != c.charAt(13) - '0') {
            throw new EntradaInvalidaException("CNPJ inválido.");
        }
        return c;
    }

    public static String cep(String bruto) {
        String c = soDigitos(bruto);
        if (c.length() != 8) throw new EntradaInvalidaException("CEP inválido: informe 8 dígitos.");
        return c;
    }

    /** Completa uma raiz de 12 dígitos (8 da raiz + 4 da ordem) com os dois dígitos verificadores. */
    public static String completarCnpj(String raiz12) {
        String base = raiz12 + dv(raiz12, 12, PESOS_12);
        return base + dv(base, 13, PESOS_13);
    }

    private static int dv(String c, int len, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < len; i++) soma += (c.charAt(i) - '0') * pesos[i];
        int r = soma % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static String soDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
