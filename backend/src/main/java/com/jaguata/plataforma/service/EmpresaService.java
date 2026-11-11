package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.dto.Requests.EmpresaRequest;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
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

    /**
     * TODO: integrar com a Receita Federal para obter CNAE e porte pelo faturamento oficial.
     * Por ora o porte vem no payload.
     */
    @Transactional
    public Empresa criar(EmpresaRequest r) {
        if (empresas.existsByCnpj(r.cnpj())) {
            throw new RegraNegocioException("Já existe empresa cadastrada com este CNPJ.");
        }
        Empresa e = new Empresa();
        e.setCnpj(r.cnpj());
        e.setRazaoSocial(r.razaoSocial());
        e.setCnaePrincipal(r.cnaePrincipal());
        e.setPorteReceita(r.porteReceita());
        e.setCep(r.cep());
        e.setLat(r.lat());
        e.setLng(r.lng());
        return empresas.save(e);
    }

    @Transactional(readOnly = true)
    public Empresa buscar(UUID id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Empresa", id));
    }
}
