package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.PorteEmpresa;
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
@Table(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Pattern(regexp = "\\d{14}")
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @NotBlank
    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @NotBlank
    @Column(name = "cnae_principal", nullable = false, length = 10)
    private String cnaePrincipal;

    /** Definido pela Receita (faturamento); nunca pelo nº de funcionários. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "porte_receita", nullable = false)
    private PorteEmpresa porteReceita;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "empresa", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Vaga> vagas = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public void addVaga(Vaga vaga) {
        vagas.add(vaga);
        vaga.setEmpresa(this);
    }
}
