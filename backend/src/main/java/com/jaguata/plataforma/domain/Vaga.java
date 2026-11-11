package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vagas")
@Getter
@Setter
@NoArgsConstructor
public class Vaga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    /** Cargo escolhido pela empresa a partir do cardápio fechado. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "nivel_cargo", nullable = false)
    private NivelCargo nivelCargo;

    /** Título visto pela ONG (Matriz de Complexidade). Calculado no backend. */
    @Column(name = "titulo_exibicao", nullable = false, length = 80)
    private String tituloExibicao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "tipo_contrato", nullable = false)
    private TipoContrato tipoContrato;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modelo_trabalho", nullable = false)
    private ModeloTrabalho modeloTrabalho;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    @Column(name = "cep_atuacao", nullable = false, length = 8)
    private String cepAtuacao;

    @DecimalMin("-90") @DecimalMax("90")
    @Column(nullable = false)
    private double lat;

    @DecimalMin("-180") @DecimalMax("180")
    @Column(nullable = false)
    private double lng;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private StatusVaga status = StatusVaga.ABERTA;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "vaga")
    private List<Match> matches = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        aplicarMatrizDeComplexidade();
    }

    @PreUpdate
    void onUpdate() {
        aplicarMatrizDeComplexidade();
    }

    private void aplicarMatrizDeComplexidade() {
        if (empresa != null && nivelCargo != null) {
            tituloExibicao = nivelCargo.tituloExibicaoPara(empresa.getPorteReceita());
        }
    }
}
