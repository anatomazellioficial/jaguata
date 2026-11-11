package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Requests.StatusMatchRequest;
import com.jaguata.plataforma.dto.Responses.MatchResponse;
import com.jaguata.plataforma.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matches;

    public MatchController(MatchService matches) {
        this.matches = matches;
    }

    /** Avança o funil (SUGERIDO -> ENVIADO -> ENTREVISTA -> CONTRATADO | RECUSADO). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("@acesso.avancarMatch(#id, #req.status())")
    public MatchResponse avancar(@PathVariable UUID id, @Valid @RequestBody StatusMatchRequest req) {
        return MatchResponse.from(matches.avancar(id, req.status()));
    }
}
