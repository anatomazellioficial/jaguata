package com.jaguata.plataforma.integracao;

/** Consulta de CNPJ na Receita Federal. A implementação real usa a BrasilAPI. */
public interface ReceitaClient {

    /**
     * @param cnpj 14 dígitos, já validado
     * @throws com.jaguata.plataforma.exception.ConsultaNaoEncontradaException se o CNPJ não existir
     * @throws com.jaguata.plataforma.exception.ServicoExternoException         se a fonte falhar ou demorar
     */
    DadosCnpj consultar(String cnpj);
}
