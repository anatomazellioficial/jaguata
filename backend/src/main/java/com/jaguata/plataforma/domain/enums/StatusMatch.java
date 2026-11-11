package com.jaguata.plataforma.domain.enums;

public enum StatusMatch {
    SUGERIDO, ENVIADO, ENTREVISTA, CONTRATADO, RECUSADO;

    /** Funil linear; RECUSADO pode ocorrer em qualquer etapa aberta. CONTRATADO e RECUSADO são finais. */
    public boolean podeTransitarPara(StatusMatch destino) {
        return switch (this) {
            case SUGERIDO -> destino == ENVIADO || destino == RECUSADO;
            case ENVIADO -> destino == ENTREVISTA || destino == RECUSADO;
            case ENTREVISTA -> destino == CONTRATADO || destino == RECUSADO;
            case CONTRATADO, RECUSADO -> false;
        };
    }
}
