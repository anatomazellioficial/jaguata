package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    Optional<Sessao> findByTokenHash(String tokenHash);

    @Query("select distinct s.familia from Sessao s where s.usuarioId = :usuarioId and s.revogadaEm is null")
    java.util.List<UUID> familiasAtivas(@Param("usuarioId") UUID usuarioId);

    boolean existsByFamiliaAndRevogadaEmIsNullAndExpiraEmAfter(UUID familia, OffsetDateTime agora);

    long countByUsuarioIdAndRevogadaEmIsNullAndExpiraEmAfter(UUID usuarioId, OffsetDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sessao s set s.revogadaEm = :agora where s.familia = :familia and s.revogadaEm is null")
    int revogarFamilia(@Param("familia") UUID familia, @Param("agora") OffsetDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Sessao s set s.revogadaEm = :agora where s.usuarioId = :usuarioId and s.revogadaEm is null")
    int revogarTodas(@Param("usuarioId") UUID usuarioId, @Param("agora") OffsetDateTime agora);

    @Modifying
    @Query("delete from Sessao s where s.expiraEm < :limite")
    int apagarExpiradas(@Param("limite") OffsetDateTime limite);
}
