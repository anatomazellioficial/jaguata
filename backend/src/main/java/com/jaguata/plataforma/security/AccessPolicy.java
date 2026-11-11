package com.jaguata.plataforma.security;

import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Regras de posse usadas em @PreAuthorize("@acesso.xxx(...)").
 * ADMIN passa em tudo; EMPRESA só age sobre os próprios recursos; ONG idem.
 */
@Component("acesso")
public class AccessPolicy {

    private final VagaRepository vagas;
    private final MatchRepository matches;

    public AccessPolicy(VagaRepository vagas, MatchRepository matches) {
        this.vagas = vagas;
        this.matches = matches;
    }

    private Optional<Jwt> jwt() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof Jwt j ? Optional.of(j) : Optional.empty();
    }

    private boolean papel(Jwt j, String papel) {
        return papel.equals(j.getClaimAsString("papel"));
    }

    private boolean claimIgual(Jwt j, String claim, UUID id) {
        return id != null && id.toString().equals(j.getClaimAsString(claim));
    }

    public boolean empresa(UUID empresaId) {
        return jwt().map(j -> papel(j, "ADMIN")
                || (papel(j, "EMPRESA") && claimIgual(j, "empresaId", empresaId))).orElse(false);
    }

    public boolean ong(UUID ongId) {
        return jwt().map(j -> papel(j, "ADMIN")
                || (papel(j, "ONG") && claimIgual(j, "ongId", ongId))).orElse(false);
    }

    /** A vaga pertence à empresa logada. */
    public boolean vaga(UUID vagaId) {
        return jwt().map(j -> papel(j, "ADMIN")
                || (papel(j, "EMPRESA")
                && j.getClaimAsString("empresaId") != null
                && vagas.existsByIdAndEmpresaId(vagaId, UUID.fromString(j.getClaimAsString("empresaId")))))
                .orElse(false);
    }

    /**
     * Quem pode mover o funil:
     * ENVIADO -> a ONG dona do match; ENTREVISTA/CONTRATADO -> a empresa dona da vaga;
     * RECUSADO -> qualquer uma das duas partes. Match inexistente passa (o serviço responde 404).
     */
    @Transactional(readOnly = true)
    public boolean avancarMatch(UUID matchId, StatusMatch destino) {
        Optional<Jwt> jwt = jwt();
        if (jwt.isEmpty()) return false;
        Jwt j = jwt.get();
        if (papel(j, "ADMIN")) return true;

        Match m = matches.findDetalhadoById(matchId).orElse(null);
        if (m == null) return true;

        boolean ongDona = papel(j, "ONG") && claimIgual(j, "ongId", m.getOng().getId());
        boolean empresaDona = papel(j, "EMPRESA")
                && claimIgual(j, "empresaId", m.getVaga().getEmpresa().getId());

        return switch (destino) {
            case ENVIADO -> ongDona;
            case ENTREVISTA, CONTRATADO -> empresaDona;
            case RECUSADO -> ongDona || empresaDona;
            case SUGERIDO -> false;
        };
    }
}
