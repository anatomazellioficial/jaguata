package com.jaguata.plataforma.domain;

import com.jaguata.plataforma.domain.enums.PapelUsuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private PapelUsuario papel;

    @Column(name = "empresa_id")
    private UUID empresaId;

    @Column(name = "ong_id")
    private UUID ongId;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = false;

    @Column(name = "email_verificado_em")
    private OffsetDateTime emailVerificadoEm;

    /** Versão dos Termos de Uso/Política de Privacidade aceita no cadastro. */
    @Column(name = "termos_versao", length = 20)
    private String termosVersao;

    @Column(name = "termos_aceitos_em")
    private OffsetDateTime termosAceitosEm;

    @Column(name = "senha_alterada_em")
    private OffsetDateTime senhaAlteradaEm;

    /** Segredo TOTP cifrado (AES-GCM). Nulo se nunca configurado. */
    @Column(name = "totp_segredo", length = 255)
    private String totpSegredo;

    @Column(name = "notificacoes_email", nullable = false)
    private boolean notificacoesEmail = true;

    @Column(name = "totp_ativo", nullable = false)
    private boolean totpAtivo = false;

    @Column(name = "totp_ultimo_passo")
    private Long totpUltimoPasso;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
