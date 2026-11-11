package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Requests.VagaRequest;
import com.jaguata.plataforma.dto.Responses.EmpresaResponse;
import com.jaguata.plataforma.dto.Responses.VagaResponse;
import com.jaguata.plataforma.service.EmpresaService;
import com.jaguata.plataforma.service.VagaService;
import com.jaguata.plataforma.service.VagaService.CriarVagaCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** O cadastro de empresas é feito em /api/auth/registro/empresa. */
@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService empresas;
    private final VagaService vagas;

    public EmpresaController(EmpresaService empresas, VagaService vagas) {
        this.empresas = empresas;
        this.vagas = vagas;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@acesso.empresa(#id)")
    public EmpresaResponse buscar(@PathVariable UUID id) {
        return EmpresaResponse.from(empresas.buscar(id));
    }

    @PostMapping("/{id}/vagas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@acesso.empresa(#id)")
    public VagaResponse criarVaga(@PathVariable UUID id, @Valid @RequestBody VagaRequest req) {
        return VagaResponse.from(vagas.criar(id, new CriarVagaCommand(req.nivelCargo(), req.tipoContrato(),
                req.modeloTrabalho(), req.cepAtuacao(), req.lat(), req.lng())));
    }

    @GetMapping("/{id}/vagas")
    @PreAuthorize("@acesso.empresa(#id)")
    public List<VagaResponse> listarVagas(@PathVariable UUID id) {
        return vagas.listarPorEmpresa(id).stream().map(VagaResponse::from).toList();
    }
}
