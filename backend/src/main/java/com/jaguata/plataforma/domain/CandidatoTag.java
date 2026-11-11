package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.CategoriaTag;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.util.UUID;

@Entity
@Table(name = "candidato_tags")
@Getter
@Setter
@NoArgsConstructor
public class CandidatoTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private CategoriaTag categoria;

    /** Ex.: "PCD", "Egresso", "50+". */
    @NotBlank
    @Column(nullable = false, length = 80)
    private String tag;

    public CandidatoTag(CategoriaTag categoria, String tag) {
        this.categoria = categoria;
        this.tag = tag;
    }
}
