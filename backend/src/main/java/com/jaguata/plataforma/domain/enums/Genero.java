package com.jaguata.plataforma.domain.enums;

/** Autodeclaração, opcional. Só é usada em vagas afirmativas e nunca é exibida às empresas. */
public enum Genero {
    MULHER("Mulher"),
    HOMEM("Homem"),
    MULHER_TRANS("Mulher trans"),
    HOMEM_TRANS("Homem trans"),
    NAO_BINARIO("Não binário"),
    OUTRO("Outro");

    private final String rotulo;

    Genero(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
