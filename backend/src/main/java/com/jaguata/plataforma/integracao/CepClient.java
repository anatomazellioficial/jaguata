package com.jaguata.plataforma.integracao;

/** Resolve um CEP em endereço + coordenadas. */
public interface CepClient {

    /**
     * @param cep 8 dígitos, já validado
     * @throws com.jaguata.plataforma.exception.ConsultaNaoEncontradaException se o CEP não existir ou não puder ser localizado
     * @throws com.jaguata.plataforma.exception.ServicoExternoException         se as fontes falharem ou demorarem
     */
    Localizacao localizar(String cep);
}
