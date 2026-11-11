package com.jaguata.plataforma.repository;

import java.util.UUID;

/** Projeção do resultado da busca geográfica de candidatos elegíveis. */
public interface CandidatoProximo {
    UUID getCandidatoId();

    Double getDistanciaMetros();
}
