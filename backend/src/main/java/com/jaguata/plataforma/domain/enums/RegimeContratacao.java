package com.jaguata.plataforma.domain.enums;

public enum RegimeContratacao {
    CLT("CLT"),
    PJ("PJ"),
    COOPERADO("Cooperado");

    private final String rotulo;

    RegimeContratacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
