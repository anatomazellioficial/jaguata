package com.jaguata.plataforma.integracao;

import com.jaguata.plataforma.domain.enums.PorteEmpresa;

/**
 * Dados do CNPJ na Receita Federal. Contém apenas o que a plataforma usa: o quadro societário
 * (nomes de pessoas físicas) vem na resposta da fonte, mas é descartado de propósito.
 */
public record DadosCnpj(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        /** CNAE fiscal principal, 7 dígitos. */
        String cnaePrincipal,
        PorteEmpresa porte,
        boolean ativa,
        String situacao,
        String cep,
        String logradouro,
        String bairro,
        String municipio,
        String uf,
        String naturezaJuridica,
        /** Natureza jurídica do grupo 3xxx (entidades sem fins lucrativos). */
        boolean entidadeSemFinsLucrativos) {
}
