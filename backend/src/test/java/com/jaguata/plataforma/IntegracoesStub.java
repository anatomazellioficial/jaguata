package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.enums.PorteEmpresa;
import com.jaguata.plataforma.exception.ConsultaNaoEncontradaException;
import com.jaguata.plataforma.exception.ServicoExternoException;
import com.jaguata.plataforma.integracao.CepClient;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.integracao.Localizacao.Precisao;
import com.jaguata.plataforma.integracao.ReceitaClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Substitui Receita e CEP por dublês determinísticos (nenhum teste de API depende da internet).
 * CNPJ pelo prefixo: 11=ME, 22=EPP, 33=MEI, 44=DEMAIS, 55=ONG (natureza 3999), 77=serviço fora do ar,
 * 88=não encontrado, 99=situação "BAIXADA"; qualquer outro = ME ativa.
 * CEP: 01001000=Sé, 01310100=Av. Paulista, 13015904=Campinas, 00000000=não encontrado, 99999999=fora do ar.
 */
@TestConfiguration
public class IntegracoesStub {

    @Bean
    @Primary
    ReceitaClient receitaStub() {
        return cnpj -> {
            String p = cnpj.substring(0, 2);
            if (p.equals("77")) throw new ServicoExternoException("Serviço de consulta (Receita Federal) indisponível no momento.");
            if (p.equals("88")) throw new ConsultaNaoEncontradaException("CNPJ não encontrado na Receita Federal.");
            PorteEmpresa porte = switch (p) {
                case "22" -> PorteEmpresa.EPP;
                case "33" -> PorteEmpresa.MEI;
                case "44" -> PorteEmpresa.DEMAIS;
                default -> PorteEmpresa.ME;
            };
            boolean ativa = !p.equals("99");
            boolean ong = p.equals("55");
            return new DadosCnpj(cnpj, (ong ? "Associação Teste " : "Empresa Teste ") + cnpj, "Fantasia", "6201501", porte,
                    ativa, ativa ? "ATIVA" : "BAIXADA", "01310100", "Avenida Paulista", "Bela Vista", "SAO PAULO", "SP",
                    ong ? "Associação Privada" : "Sociedade Empresária Limitada", ong);
        };
    }

    @Bean
    @Primary
    CepClient cepStub() {
        return cep -> switch (cep) {
            case "00000000" -> throw new ConsultaNaoEncontradaException("CEP não encontrado.");
            case "99999999" -> throw new ServicoExternoException("Serviço de consulta (CEP) indisponível no momento.");
            case "01001000" -> new Localizacao(cep, "Praça da Sé", "Sé", "São Paulo", "SP", -23.5505, -46.6333, Precisao.CEP);
            case "01310100" -> new Localizacao(cep, "Avenida Paulista", "Bela Vista", "São Paulo", "SP", -23.5614, -46.6559, Precisao.CEP);
            case "13015904" -> new Localizacao(cep, "Rua Teste", "Centro", "Campinas", "SP", -22.9099, -47.0626, Precisao.RUA);
            default -> new Localizacao(cep, "Rua Genérica", "Centro", "São Paulo", "SP", -23.55, -46.63, Precisao.CIDADE);
        };
    }
}
