package com.jaguata.plataforma.domain.enums;

import java.util.Set;

/**
 * Escolaridade em ordem crescente (a ordem de declaração é usada em comparações, inclusive no banco,
 * onde o tipo enum também é ordenado).
 */
public enum Escolaridade {
    FUNDAMENTAL_INCOMPLETO("Fundamental incompleto"),
    FUNDAMENTAL("Fundamental completo"),
    MEDIO_INCOMPLETO("Médio incompleto"),
    MEDIO("Médio completo"),
    TECNICO_INCOMPLETO("Técnico em andamento"),
    TECNICO("Técnico completo"),
    SUPERIOR_INCOMPLETO("Superior em andamento"),
    SUPERIOR("Superior completo"),
    POS("Pós-graduação");

    /** Estágio exige estar cursando ensino técnico ou superior. */
    public static final Set<Escolaridade> ELEGIVEIS_ESTAGIO = Set.of(TECNICO_INCOMPLETO, SUPERIOR_INCOMPLETO);

    private final String rotulo;

    Escolaridade(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean atende(Escolaridade minima) {
        return minima == null || compareTo(minima) >= 0;
    }
}
