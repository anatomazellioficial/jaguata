package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "candidatos")
@Getter
@Setter
@NoArgsConstructor
public class Candidato {

    public static final int IDADE_MINIMA = 18;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ong_id", nullable = false)
    private Ong ong;

    @NotBlank
    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @NotNull
    @Past
    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @NotBlank
    @Pattern(regexp = "\\d{8}")
    @Column(nullable = false, length = 8)
    private String cep;

    @DecimalMin("-90") @DecimalMax("90")
    @Column(nullable = false)
    private double lat;

    @DecimalMin("-180") @DecimalMax("180")
    @Column(nullable = false)
    private double lng;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "modelo_trabalho_pref", nullable = false)
    private ModeloTrabalho modeloTrabalhoPref;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "candidato", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CandidatoTag> tags = new LinkedHashSet<>();

    public static int calcularIdade(LocalDate nascimento, LocalDate hoje) {
        return Period.between(nascimento, hoje).getYears();
    }

    public int getIdade() {
        return calcularIdade(dataNascimento, LocalDate.now());
    }

    /** Regra de negócio (espelha o CHECK/trigger do banco): 18 anos completos. */
    @PrePersist
    void onCreate() {
        validarIdadeMinima();
        createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        validarIdadeMinima();
    }

    private void validarIdadeMinima() {
        if (dataNascimento == null || calcularIdade(dataNascimento, LocalDate.now()) < IDADE_MINIMA) {
            throw new IllegalArgumentException(
                    "Candidato deve ter no mínimo " + IDADE_MINIMA + " anos completos.");
        }
    }

    public void addTag(CandidatoTag tag) {
        tags.add(tag);
        tag.setCandidato(this);
    }
}
