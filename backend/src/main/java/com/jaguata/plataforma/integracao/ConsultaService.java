package com.jaguata.plataforma.integracao;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fachada das consultas externas: valida a entrada, consulta com cache e propaga falhas sem cachear.
 * O cache reduz a carga sobre as APIs públicas (e o tempo de resposta); falhas nunca são guardadas.
 */
@Service
public class ConsultaService {

    private final ReceitaClient receita;
    private final CepClient cepClient;
    private final Cache<String, DadosCnpj> cnpjs;
    private final Cache<String, Localizacao> ceps;

    public ConsultaService(ReceitaClient receita, CepClient cepClient, IntegracoesProperties props) {
        this.receita = receita;
        this.cepClient = cepClient;
        this.cnpjs = Caffeine.newBuilder().maximumSize(20_000)
                .expireAfterWrite(Duration.ofHours(props.cnpjCacheHours())).build();
        this.ceps = Caffeine.newBuilder().maximumSize(50_000)
                .expireAfterWrite(Duration.ofDays(props.cepCacheDays())).build();
    }

    /** @throws com.jaguata.plataforma.exception.EntradaInvalidaException CNPJ malformado */
    public DadosCnpj cnpj(String bruto) {
        String cnpj = Documentos.cnpj(bruto);
        return cnpjs.get(cnpj, receita::consultar);
    }

    /** @throws com.jaguata.plataforma.exception.EntradaInvalidaException CEP malformado */
    public Localizacao cep(String bruto) {
        String cep = Documentos.cep(bruto);
        return ceps.get(cep, cepClient::localizar);
    }
}
