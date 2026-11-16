package com.jaguata.plataforma.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Registro de ações sensíveis. O detalhe nunca contém dados pessoais (nomes, e-mails, documentos). */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private OffsetDateTime quando = OffsetDateTime.now();

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(length = 20)
    private String papel;

    @Column(nullable = false, length = 60)
    private String acao;

    @Column(length = 40)
    private String entidade;

    @Column(name = "entidade_id")
    private UUID entidadeId;

    @Column(length = 300)
    private String detalhe;
}
