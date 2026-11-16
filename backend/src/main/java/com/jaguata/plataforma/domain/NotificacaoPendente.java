package com.jaguata.plataforma.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Aviso à espera do resumo diário. Guarda só tipo, vaga e quantidade: nunca dados de candidatos. */
@Entity
@Table(name = "notificacoes_pendentes")
@Getter
@Setter
@NoArgsConstructor
public class NotificacaoPendente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "titulo_vaga", length = 120)
    private String tituloVaga;

    @Column(nullable = false)
    private int quantidade = 1;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm = OffsetDateTime.now();

    public NotificacaoPendente(UUID usuarioId, String tipo, String tituloVaga, int quantidade) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.tituloVaga = tituloVaga == null ? null : tituloVaga.substring(0, Math.min(120, tituloVaga.length()));
        this.quantidade = quantidade;
    }
}
