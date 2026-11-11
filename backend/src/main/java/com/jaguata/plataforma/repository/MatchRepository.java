package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    List<Match> findByVagaIdOrderByMatchScoreDesc(UUID vagaId);

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    Optional<Match> findDetalhadoById(UUID id);

    long countByStatus(StatusMatch status);

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    List<Match> findByOngIdAndStatus(UUID ongId, StatusMatch status);
}
