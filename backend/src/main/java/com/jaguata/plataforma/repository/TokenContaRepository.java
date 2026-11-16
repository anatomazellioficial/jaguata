package com.jaguata.plataforma.repository;

import com.jaguata.plataforma.domain.TokenConta;
import com.jaguata.plataforma.domain.enums.TipoTokenConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TokenContaRepository extends JpaRepository<TokenConta, UUID> {

    Optional<TokenConta> findByTokenHash(String tokenHash);

    /** Invalida tokens pendentes do mesmo tipo (só o último link enviado vale). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TokenConta t set t.usadoEm = :agora where t.usuarioId = :usuarioId and t.tipo = :tipo and t.usadoEm is null")
    int invalidarPendentes(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoTokenConta tipo,
                           @Param("agora") OffsetDateTime agora);

    boolean existsByUsuarioIdAndTipoAndCriadoEmAfter(UUID usuarioId, TipoTokenConta tipo, OffsetDateTime depois);

    @Modifying
    @Query("delete from TokenConta t where t.expiraEm < :limite")
    int apagarExpirados(@Param("limite") OffsetDateTime limite);
}
