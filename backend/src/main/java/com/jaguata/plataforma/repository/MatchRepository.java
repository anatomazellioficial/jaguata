package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    List<Match> findByVagaIdOrderByMatchScoreDesc(UUID vagaId);

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    Optional<Match> findDetalhadoById(UUID id);

    long countByStatus(StatusMatch status);

    @EntityGraph(attributePaths = {"vaga"})
    List<Match> findByCandidatoIdOrderByCreatedAtDesc(UUID candidatoId);

    /** Candidato anonimizado: encerra as pendências abertas dele. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Match m set m.status = :novo, m.updatedAt = :agora
             where m.candidato.id = :candidatoId and m.status in :abertos
            """)
    int encerrarPendenciasDoCandidato(@Param("candidatoId") UUID candidatoId,
                                      @Param("abertos") Collection<StatusMatch> abertos,
                                      @Param("novo") StatusMatch novo, @Param("agora") OffsetDateTime agora);

    boolean existsByCandidatoIdAndStatus(UUID candidatoId, StatusMatch status);

    boolean existsByVagaIdAndStatusIn(UUID vagaId, Collection<StatusMatch> status);

    /** Descarta matches de uma vaga nos status informados (ex.: sugestões que ficaram velhas após uma edição). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Match m where m.vaga.id = :vagaId and m.status in :status")
    int apagarDaVaga(@Param("vagaId") UUID vagaId, @Param("status") Collection<StatusMatch> status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Match m where m.candidato.id = :candidatoId and m.status in :status")
    int apagarDoCandidato(@Param("candidatoId") UUID candidatoId, @Param("status") Collection<StatusMatch> status);

    /**
     * Vaga preenchida: encerra como RECUSADO as demais pendências (sugeridas, enviadas ou em entrevista),
     * para não sobrarem itens sem saída na fila das ONGs. Retorna quantas foram encerradas.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Match m set m.status = :novo, m.updatedAt = :agora
             where m.vaga.id = :vagaId and m.id <> :exceto and m.status in :abertos
            """)
    int encerrarPendenciasDaVaga(@Param("vagaId") UUID vagaId, @Param("exceto") UUID exceto,
                                 @Param("abertos") Collection<StatusMatch> abertos,
                                 @Param("novo") StatusMatch novo,
                                 @Param("agora") OffsetDateTime agora);

    @EntityGraph(attributePaths = {"vaga", "candidato"})
    List<Match> findByOngIdAndStatus(UUID ongId, StatusMatch status);
}
