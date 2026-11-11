package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Vaga;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.domain.enums.TipoContrato;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.repository.EmpresaRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VagaService {

    public record CriarVagaCommand(
            @NotNull NivelCargo nivelCargo,
            @NotNull TipoContrato tipoContrato,
            @NotNull ModeloTrabalho modeloTrabalho,
            @NotNull @Pattern(regexp = "\\d{8}") String cepAtuacao,
            double lat,
            double lng) {
    }

    private final VagaRepository vagas;
    private final EmpresaRepository empresas;

    public VagaService(VagaRepository vagas, EmpresaRepository empresas) {
        this.vagas = vagas;
        this.empresas = empresas;
    }

    /**
     * O cargo vem do enum (cardápio fechado). O título exibido à ONG é derivado
     * do porte da empresa pela Matriz de Complexidade (callback de Vaga).
     */
    @Transactional
    public Vaga criar(UUID empresaId, CriarVagaCommand cmd) {
        Empresa empresa = empresas.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa", empresaId));

        Vaga vaga = new Vaga();
        vaga.setEmpresa(empresa);
        vaga.setNivelCargo(cmd.nivelCargo());
        vaga.setTipoContrato(cmd.tipoContrato());
        vaga.setModeloTrabalho(cmd.modeloTrabalho());
        vaga.setCepAtuacao(cmd.cepAtuacao());
        vaga.setLat(cmd.lat());
        vaga.setLng(cmd.lng());
        vaga.setStatus(StatusVaga.ABERTA);
        return vagas.save(vaga);
    }

    @Transactional(readOnly = true)
    public Vaga buscar(UUID vagaId) {
        return vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
    }

    @Transactional(readOnly = true)
    public java.util.List<Vaga> listarPorEmpresa(UUID empresaId) {
        if (!empresas.existsById(empresaId)) {
            throw new RecursoNaoEncontradoException("Empresa", empresaId);
        }
        return vagas.findByEmpresaId(empresaId);
    }

    @Transactional
    public Vaga alterarStatus(UUID vagaId, StatusVaga status) {
        Vaga vaga = vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
        vaga.setStatus(status);
        return vaga;
    }
}
