package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.repository.EmpresaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmpresaService {

    private final EmpresaRepository empresas;

    public EmpresaService(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    public boolean cnpjCadastrado(String cnpj) {
        return empresas.existsByCnpj(cnpj);
    }

    /**
     * Razão social, CNAE e porte vêm da Receita Federal e as coordenadas, do CEP da sede: nada disso é
     * informado pelo usuário. O porte, portanto, nunca depende de número de funcionários.
     */
    @Transactional
    public Empresa criar(DadosCnpj d, Localizacao loc) {
        if (empresas.existsByCnpj(d.cnpj())) {
            throw new RegraNegocioException("Já existe empresa cadastrada com este CNPJ.");
        }
        Empresa e = new Empresa();
        e.setCnpj(d.cnpj());
        e.setRazaoSocial(d.razaoSocial());
        e.setCnaePrincipal(d.cnaePrincipal());
        e.setPorteReceita(d.porte());
        e.setCep(loc.cep());
        e.setLat(loc.lat());
        e.setLng(loc.lng());
        return empresas.save(e);
    }

    @Transactional(readOnly = true)
    public Empresa buscar(UUID id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Empresa", id));
    }
}
