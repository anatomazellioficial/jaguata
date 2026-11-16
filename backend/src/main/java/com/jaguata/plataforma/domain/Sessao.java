package com.jaguata.plataforma.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Refresh token rotativo. Cada renovação revoga o anterior e cria outro na mesma família. */
@Entity
@Table(name = "sessoes")
@Getter
@Setter
@NoArgsConstructor
public class Sessao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private UUID familia;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm = OffsetDateTime.now();

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "revogada_em")
    private OffsetDateTime revogadaEm;

    @Column(name = "substituida_por")
    private UUID substituidaPor;

    @Column(name = "user_agent", length = 200)
    private String userAgent;
}
