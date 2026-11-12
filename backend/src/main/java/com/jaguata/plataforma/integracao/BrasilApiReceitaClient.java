package com.jaguata.plataforma.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.jaguata.plataforma.domain.enums.PorteEmpresa;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.ServicoExternoException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * CNPJ na Receita Federal via BrasilAPI (dados públicos). Lê apenas os campos necessários; o quadro
 * societário e demais dados pessoais presentes na resposta nunca são lidos nem repassados.
 */
@Component
public class BrasilApiReceitaClient implements ReceitaClient {

    private static final int SITUACAO_ATIVA = 2;

    private final RestClient http;

    public BrasilApiReceitaClient(IntegracoesProperties props) {
        this.http = HttpJson.criar(props.brasilApiUrl(), props);
    }

    @Override
    public DadosCnpj consultar(String cnpj) {
        JsonNode n = HttpJson.get(http, "Receita Federal",
                        c -> c.get().uri("/api/cnpj/v1/{cnpj}", cnpj).retrieve().body(JsonNode.class))
                .orElseThrow(() -> new ConsultaNaoEncontradaException("CNPJ não encontrado na Receita Federal."));
        return mapear(cnpj, n);
    }

    static DadosCnpj mapear(String cnpj, JsonNode n) {
        String razao = HttpJson.texto(n, "razao_social");
        JsonNode cnae = n.path("cnae_fiscal");
        if (razao.isEmpty() || !cnae.canConvertToLong()) {
            throw new ServicoExternoException("Resposta da Receita Federal incompleta.");
        }
        int natureza = n.path("codigo_natureza_juridica").asInt(0);
        return new DadosCnpj(
                cnpj,
                razao,
                HttpJson.texto(n, "nome_fantasia"),
                String.format("%07d", cnae.asLong()),
                porte(n),
                n.path("situacao_cadastral").asInt(0) == SITUACAO_ATIVA,
                HttpJson.texto(n, "descricao_situacao_cadastral"),
                String.format("%8s", HttpJson.texto(n, "cep").replaceAll("\\D", "")).replace(' ', '0'),
                HttpJson.texto(n, "logradouro"),
                HttpJson.texto(n, "bairro"),
                HttpJson.texto(n, "municipio"),
                HttpJson.texto(n, "uf"),
                HttpJson.texto(n, "natureza_juridica"),
                natureza >= 3000 && natureza < 4000);
    }

    /**
     * Porte conforme a Receita: optante pelo MEI → MEI; código 1 → ME; 3 → EPP; qualquer outro
     * (5 = demais, 0/ausente = não informado) → DEMAIS, que aplica a régua estrita de cargos.
     */
    static PorteEmpresa porte(JsonNode n) {
        if (n.path("opcao_pelo_mei").asBoolean(false)) return PorteEmpresa.MEI;
        return switch (n.path("codigo_porte").asInt(0)) {
            case 1 -> PorteEmpresa.ME;
            case 3 -> PorteEmpresa.EPP;
            default -> PorteEmpresa.DEMAIS;
        };
    }
}
