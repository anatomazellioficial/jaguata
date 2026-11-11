package com.jaguata.plataforma.domain.enums;

/** Porte definido exclusivamente pelo faturamento (Receita Federal). */
public enum PorteEmpresa {
    MEI, ME, EPP, DEMAIS;

    public boolean isMicroOuPequena() {
        return this == MEI || this == ME || this == EPP;
    }
}
