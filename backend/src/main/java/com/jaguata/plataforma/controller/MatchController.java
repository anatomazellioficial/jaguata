package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Requests.StatusMatchRequest;
import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.dto.Responses.MatchEmpresaResponse;
import com.jaguata.plataforma.dto.Responses.MatchResponse;
import org.springframework.security.core.Authentication;
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
    public Object avancar(@PathVariable UUID id, @Valid @RequestBody StatusMatchRequest req, Authentication auth) {
        Match m = matches.avancar(id, req.status());
        boolean empresa = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EMPRESA"));
        // a empresa nunca recebe a nota numérica (ver MatchEmpresaResponse)
        return empresa ? MatchEmpresaResponse.from(m, null) : MatchResponse.from(m);
    }
}
