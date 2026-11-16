package com.jaguata.plataforma.domain.enums;

/** Categorias do IBGE. Autodeclaração, opcional. Só é usada em vagas afirmativas e nunca é exibida às empresas. */
public enum RacaCor {
    BRANCA("Branca"),
    PRETA("Preta"),
    PARDA("Parda"),
    AMARELA("Amarela"),
    INDIGENA("Indígena");

    private final String rotulo;

    RacaCor(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
