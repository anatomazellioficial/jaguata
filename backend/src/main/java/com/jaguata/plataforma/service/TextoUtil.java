package com.jaguata.plataforma.service;

import java.text.Normalizer;
import java.util.Locale;

/** Normalização de textos livres (skills e tags) para comparação: minúsculas, sem acentos, espaços colapsados. */
public final class TextoUtil {
    private TextoUtil() {
    }

    public static String chave(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
