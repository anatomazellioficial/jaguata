package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.enums.Escolaridade;
import com.jaguata.plataforma.domain.enums.Genero;
import com.jaguata.plataforma.domain.enums.GrupoPrioritario;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.RacaCor;
import com.jaguata.plataforma.domain.enums.RegimeContratacao;
import com.jaguata.plataforma.domain.enums.SetorAtuacao;
import com.jaguata.plataforma.dto.Requests.StatusVagaRequest;
import com.jaguata.plataforma.dto.Responses.CargoResponse;
import com.jaguata.plataforma.dto.Responses.MatchResponse;
import com.jaguata.plataforma.dto.Responses.VagaResponse;
import com.jaguata.plataforma.service.MatchService;
import com.jaguata.plataforma.service.VagaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Validated
public class VagaController {

    private final VagaService vagas;
    private final MatchService matches;

    public VagaController(VagaService vagas, MatchService matches) {
        this.vagas = vagas;
        this.matches = matches;
    }

    /** Cardápio fechado de cargos, para o frontend montar o seletor. */
    @GetMapping("/cardapio/cargos")
    public List<CargoResponse> cardapio() {
        return Arrays.stream(NivelCargo.values()).map(c -> new CargoResponse(c, c.getRotulo())).toList();
    }

    public record Opcao(String codigo, String rotulo) {
    }

    /** Catálogos para os formulários de perfil do candidato, requisitos da vaga e áreas da ONG. */
    @GetMapping("/cardapio/perfil")
    public Map<String, List<Opcao>> catalogoPerfil() {
        Map<String, List<Opcao>> m = new LinkedHashMap<>();
        m.put("escolaridades", Arrays.stream(Escolaridade.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        m.put("regimes", Arrays.stream(RegimeContratacao.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        m.put("setores", Arrays.stream(SetorAtuacao.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        m.put("grupos", Arrays.stream(GrupoPrioritario.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        m.put("generos", Arrays.stream(Genero.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        m.put("racas", Arrays.stream(RacaCor.values()).map(e -> new Opcao(e.name(), e.getRotulo())).toList());
        return m;
    }

    @GetMapping("/vagas/{id}")
    @PreAuthorize("@acesso.vaga(#id)")
    public VagaResponse buscar(@PathVariable UUID id) {
        return VagaResponse.from(vagas.buscar(id));
    }

    @PatchMapping("/vagas/{id}/status")
    @PreAuthorize("@acesso.vaga(#id)")
    public VagaResponse alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusVagaRequest req) {
        return VagaResponse.from(vagas.alterarStatus(id, req.status()));
    }

    /** Dispara o motor de match para a vaga e devolve as sugestões criadas. */
    @PostMapping("/vagas/{id}/matches/sugestoes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@acesso.vaga(#id)")
    public List<MatchResponse> gerarSugestoes(@PathVariable UUID id,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(500) double raioKm,
                                              @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite,
                                              // filtros opcionais (só critérios profissionais)
                                              @RequestParam(required = false) ModeloTrabalho modelo,
                                              @RequestParam(required = false) Escolaridade escolaridadeMin,
                                              @RequestParam(required = false) RegimeContratacao regime,
                                              @RequestParam(required = false) String skills) {
        Set<String> obrigatorias = skills == null || skills.isBlank() ? Set.of()
                : Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(15)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        MatchService.Filtros filtros = new MatchService.Filtros(modelo, escolaridadeMin, obrigatorias, regime);
        return matches.gerarSugestoes(id, raioKm, limite, filtros).stream().map(MatchResponse::from).toList();
    }

    @GetMapping("/vagas/{id}/matches")
    @PreAuthorize("@acesso.vaga(#id)")
    public List<MatchResponse> listarMatches(@PathVariable UUID id) {
        return matches.listarPorVaga(id).stream().map(MatchResponse::from).toList();
    }
}
