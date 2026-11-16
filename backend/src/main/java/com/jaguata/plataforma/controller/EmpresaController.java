package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Requests.VagaLoteRequest;
import com.jaguata.plataforma.dto.Requests.VagaRequest;
import com.jaguata.plataforma.dto.Responses.EmpresaResponse;
import com.jaguata.plataforma.dto.Responses.VagaResponse;
import com.jaguata.plataforma.integracao.ConsultaService;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.service.EmpresaService;
import com.jaguata.plataforma.service.ImpactoService;
import com.jaguata.plataforma.service.MatchService;
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
    private final ConsultaService consulta;
    private final MatchService matches;
    private final ImpactoService impacto;

    public EmpresaController(EmpresaService empresas, VagaService vagas, ConsultaService consulta,
                             MatchService matches, ImpactoService impacto) {
        this.matches = matches;
        this.impacto = impacto;
        this.empresas = empresas;
        this.vagas = vagas;
        this.consulta = consulta;
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
        Localizacao loc = consulta.cep(req.cepAtuacao());   // coordenadas vêm do CEP, nunca do cliente
        return VagaResponse.from(vagas.criar(id, new CriarVagaCommand(req.nivelCargo(), req.tipoContrato(),
                req.modeloTrabalho(), loc.cep(), loc.lat(), loc.lng(), req.escolaridadeMinima(), req.regime(),
                req.skills(), req.gruposPrioritarios(), req.justificativaAfirmativa())));
    }

    /** Cria várias vagas iguais de uma vez (até 20); cada uma tem o seu próprio funil. */
    @PostMapping("/{id}/vagas/lote")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@acesso.empresa(#id)")
    public List<VagaResponse> criarVagasEmLote(@PathVariable UUID id, @Valid @RequestBody VagaLoteRequest req) {
        VagaRequest v = req.vaga();
        Localizacao loc = consulta.cep(v.cepAtuacao());
        List<VagaResponse> criadas = new java.util.ArrayList<>();
        for (int i = 0; i < req.quantidade(); i++) {
            criadas.add(VagaResponse.from(vagas.criar(id, new CriarVagaCommand(v.nivelCargo(), v.tipoContrato(),
                    v.modeloTrabalho(), loc.cep(), loc.lat(), loc.lng(), v.escolaridadeMinima(), v.regime(),
                    v.skills(), v.gruposPrioritarios(), v.justificativaAfirmativa()))));
        }
        return criadas;
    }

    /**
     * Busca em lote para todas as vagas abertas: cada candidato vai para uma só vaga e, com {@code distribuir},
     * as sugestões são repartidas entre as ONGs.
     */
    @PostMapping("/{id}/vagas/sugestoes-em-lote")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@acesso.empresa(#id)")
    public List<MatchService.ResultadoLote> sugestoesEmLote(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") double raioKm,
            @RequestParam(defaultValue = "20") int limite,
            @RequestParam(defaultValue = "true") boolean distribuir,
            @RequestParam(required = false) com.jaguata.plataforma.domain.enums.ModeloTrabalho modelo,
            @RequestParam(required = false) com.jaguata.plataforma.domain.enums.Escolaridade escolaridadeMin,
            @RequestParam(required = false) com.jaguata.plataforma.domain.enums.RegimeContratacao regime,
            @RequestParam(required = false) Integer idadeMin,
            @RequestParam(required = false) Integer idadeMax) {
        MatchService.Filtros f = new MatchService.Filtros(modelo, escolaridadeMin, java.util.Set.of(), regime, idadeMin, idadeMax);
        return matches.gerarSugestoesEmLote(id, raioKm, limite, f, distribuir);
    }

    /** Relatório de impacto (ESG): só totais, sem recortes por atributos de candidatos. */
    @GetMapping("/{id}/impacto")
    @PreAuthorize("@acesso.empresa(#id)")
    public ImpactoService.EmpresaImpacto impacto(@PathVariable UUID id) {
        return impacto.daEmpresa(id);
    }

    @GetMapping("/{id}/vagas")
    @PreAuthorize("@acesso.empresa(#id)")
    public List<VagaResponse> listarVagas(@PathVariable UUID id) {
        return vagas.listarPorEmpresa(id).stream().map(VagaResponse::from).toList();
    }
}
