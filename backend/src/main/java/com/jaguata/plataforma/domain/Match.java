package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Funil: SUGERIDO -> ENVIADO -> ENTREVISTA -> CONTRATADO (ou RECUSADO). */
@Entity(name = "Match")
@Table(name = "matches",
        uniqueConstraints = @UniqueConstraint(name = "uq_match_vaga_candidato",
                columnNames = {"vaga_id", "candidato_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_id", nullable = false)
    private Vaga vaga;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    /** Auditoria: ONG que chancela o candidato (deve ser a ONG dona do candidato). */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @DecimalMin("0") @DecimalMax("100")
    @Column(name = "match_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchScore = BigDecimal.ZERO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private StatusMatch status = StatusMatch.SUGERIDO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        validarRegras();
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    private void validarRegras() {
        if (!candidato.getOng().getId().equals(ong.getId())) {
            throw new IllegalStateException("A ONG do match deve ser a ONG dona do candidato.");
        }
        if (vaga.getNivelCargo() == NivelCargo.APRENDIZ) {
            int idade = candidato.getIdade();
            if (idade < NivelCargo.APRENDIZ_IDADE_MIN || idade > NivelCargo.APRENDIZ_IDADE_MAX) {
                throw new IllegalStateException("Vaga de Aprendiz é restrita a candidatos de 18 a 24 anos.");
            }
        }
    }
}
