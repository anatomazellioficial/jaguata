package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.dto.Requests.CandidatoRequest;
import com.jaguata.plataforma.dto.Responses.CandidatoResponse;
import com.jaguata.plataforma.dto.Responses.MatchResponse;
import com.jaguata.plataforma.dto.Responses.OngResponse;
import com.jaguata.plataforma.integracao.ConsultaService;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.service.CandidatoService;
import com.jaguata.plataforma.service.CandidatoService.CadastrarCandidatoCommand;
import com.jaguata.plataforma.service.CandidatoService.TagInput;
import com.jaguata.plataforma.service.MatchService;
import com.jaguata.plataforma.service.OngService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** O cadastro de ONGs é feito em /api/auth/registro/ong. */
@RestController
@RequestMapping("/api/ongs")
public class OngController {

    private final OngService ongs;
    private final CandidatoService candidatos;
    private final MatchService matches;
    private final ConsultaService consulta;

    public OngController(OngService ongs, CandidatoService candidatos, MatchService matches, ConsultaService consulta) {
        this.ongs = ongs;
        this.candidatos = candidatos;
        this.matches = matches;
        this.consulta = consulta;
    }

    @PostMapping("/{id}/verificar")
    @PreAuthorize("hasRole('ADMIN')")
    public OngResponse verificar(@PathVariable UUID id) {
        return OngResponse.from(ongs.verificar(id));
    }

    @PostMapping("/{id}/candidatos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@acesso.ong(#id)")
    public CandidatoResponse cadastrarCandidato(@PathVariable UUID id, @Valid @RequestBody CandidatoRequest req) {
        List<TagInput> tags = req.tags() == null ? List.of()
                : req.tags().stream().map(t -> new TagInput(t.categoria(), t.tag())).toList();
        Localizacao loc = consulta.cep(req.cep());   // coordenadas vêm do CEP, nunca do cliente
        return CandidatoResponse.from(candidatos.cadastrar(id, new CadastrarCandidatoCommand(
                req.nomeCompleto(), req.dataNascimento(), loc.cep(), loc.lat(), loc.lng(),
                req.modeloTrabalhoPref(), tags)));
    }

    /** Fila da ONG: por padrão, os matches SUGERIDOS aguardando envio. */
    @GetMapping("/{id}/matches")
    @PreAuthorize("@acesso.ong(#id)")
    public List<MatchResponse> listarMatches(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "SUGERIDO") StatusMatch status) {
        return matches.listarPorOng(id, status).stream().map(MatchResponse::from).toList();
    }
}
