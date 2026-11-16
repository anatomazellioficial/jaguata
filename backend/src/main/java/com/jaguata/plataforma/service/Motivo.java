package com.jaguata.plataforma.service;

/**
 * Um critério do score: quantos pontos o candidato fez (de um máximo) e uma nota curta que explica.
 * Nunca contém dados sensíveis do candidato (gênero, raça/cor, tags) nem o nome do grupo priorizado.
 */
public record Motivo(String criterio, double pontos, int maximo, String nota) {
}
