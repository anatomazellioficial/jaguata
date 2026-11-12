package com.jaguata.plataforma.integracao;

/** Endereço de um CEP com coordenadas. {@code precisao} diz o quão exato é o ponto. */
public record Localizacao(
        String cep,
        String logradouro,
        String bairro,
        String cidade,
        String uf,
        double lat,
        double lng,
        Precisao precisao) {

    public enum Precisao {
        /** Ponto do CEP (fonte principal ou geocodificação por CEP). */
        CEP,
        /** Aproximado pela rua e cidade. */
        RUA,
        /** Só o centro da cidade: use com cautela em raios pequenos. */
        CIDADE
    }
}
