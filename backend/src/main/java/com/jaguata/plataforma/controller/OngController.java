package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.SetorAtuacao;
import com.jaguata.plataforma.dto.Requests.CandidatoEdicaoRequest;
import com.jaguata.plataforma.dto.Requests.CandidatoRequest;
import com.jaguata.plataforma.dto.Requests.SetoresRequest;
import com.jaguata.plataforma.dto.Responses.CandidatoEdicaoResponse;
import com.jaguata.plataforma.dto.Responses.CandidatoExportacaoResponse;
import com.jaguata.plataforma.dto.Responses.CandidatoResumoResponse;
import com.jaguata.plataforma.dto.Responses.CandidatoResponse;
import com.jaguata.plataforma.dto.Responses.PaginaResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
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
    public CandidatoResponse cadastrarCandidato(@PathVariable UUID id, @Valid @RequestBody CandidatoRequest req,
                                                @AuthenticationPrincipal Jwt jwt) {
        List<TagInput> tags = req.tags() == null ? List.of()
                : req.tags().stream().map(t -> new TagInput(t.categoria(), t.tag())).toList();
        Localizacao loc = consulta.cep(req.cep());   // coordenadas vêm do CEP, nunca do cliente
        return CandidatoResponse.from(candidatos.cadastrar(id, new CadastrarCandidatoCommand(
                req.nomeCompleto(), req.dataNascimento(), loc.cep(), loc.lat(), loc.lng(),
                req.modeloTrabalhoPref(), tags, req.escolaridade(), req.genero(), req.racaCor(), req.regimes()),
                UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{id}/candidatos/{candidatoId}")
    @PreAuthorize("@acesso.ong(#id)")
    public CandidatoEdicaoResponse candidato(@PathVariable UUID id, @PathVariable UUID candidatoId) {
        return candidatos.paraEdicao(id, candidatoId);
    }

    @PutMapping("/{id}/candidatos/{candidatoId}")
    @PreAuthorize("@acesso.ong(#id)")
    public CandidatoResponse editarCandidato(@PathVariable UUID id, @PathVariable UUID candidatoId,
                                             @Valid @RequestBody CandidatoEdicaoRequest req) {
        List<TagInput> tags = req.tags() == null ? List.of()
                : req.tags().stream().map(t -> new TagInput(t.categoria(), t.tag())).toList();
        Localizacao loc = consulta.cep(req.cep());
        return CandidatoResponse.from(candidatos.atualizar(id, candidatoId, new CadastrarCandidatoCommand(
                req.nomeCompleto(), req.dataNascimento(), loc.cep(), loc.lat(), loc.lng(),
                req.modeloTrabalhoPref(), tags, req.escolaridade(), req.genero(), req.racaCor(), req.regimes())));
    }

    /** Candidatos ativos da ONG (os anonimizados não aparecem). */
    @GetMapping("/{id}/candidatos")
    @PreAuthorize("@acesso.ong(#id)")
    public PaginaResponse<CandidatoResumoResponse> listarCandidatos(@PathVariable UUID id,
                                                                    @RequestParam(defaultValue = "0") int pagina,
                                                                    @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResponse.de(candidatos.listar(id, pagina, tamanho));
    }

    /** Direito de acesso/portabilidade: todos os dados do candidato (registra auditoria). */
    @GetMapping("/{id}/candidatos/{candidatoId}/dados")
    @PreAuthorize("@acesso.ong(#id)")
    public CandidatoExportacaoResponse exportarCandidato(@PathVariable UUID id, @PathVariable UUID candidatoId) {
        return candidatos.exportar(id, candidatoId);
    }

    /** Direito de eliminação: anonimiza os dados pessoais do candidato (registra auditoria). */
    @DeleteMapping("/{id}/candidatos/{candidatoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@acesso.ong(#id)")
    public void anonimizarCandidato(@PathVariable UUID id, @PathVariable UUID candidatoId) {
        candidatos.anonimizar(id, candidatoId);
    }

    /** Setores em que a ONG atua (cruzados com o setor da empresa no match). */
    @GetMapping("/{id}/setores")
    @PreAuthorize("@acesso.ong(#id)")
    public Set<SetorAtuacao> setores(@PathVariable UUID id) {
        return ongs.setores(id);
    }

    @PutMapping("/{id}/setores")
    @PreAuthorize("@acesso.ong(#id)")
    public Set<SetorAtuacao> atualizarSetores(@PathVariable UUID id, @Valid @RequestBody SetoresRequest req) {
        return ongs.atualizarSetores(id, req.setores());
    }

    /** Fila da ONG: por padrão, os matches SUGERIDOS aguardando envio. */
    @GetMapping("/{id}/matches")
    @PreAuthorize("@acesso.ong(#id)")
    public List<MatchResponse> listarMatches(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "SUGERIDO") StatusMatch status) {
        return matches.listarPorOng(id, status).stream().map(MatchResponse::from).toList();
    }
}
