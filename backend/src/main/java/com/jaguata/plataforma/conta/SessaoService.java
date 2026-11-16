package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.domain.Sessao;
import com.jaguata.plataforma.exception.SessaoInvalidaException;
import com.jaguata.plataforma.repository.SessaoRepository;
import com.jaguata.plataforma.security.SecurityProperties;
import com.jaguata.plataforma.security.SessaoAtiva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sessões com refresh token OPACO e ROTATIVO. O access token (JWT) é curto; quando expira, o refresh token
 * é trocado por um par novo e o antigo é revogado. Se um refresh token já usado reaparecer (roubo ou cópia),
 * a família inteira é revogada: quem tinha o token, legítimo ou não, precisa entrar de novo.
 */
@Service
public class SessaoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);

    public record Rotacao(UUID usuarioId, String novoRefreshToken, UUID sessaoId) {
    }

    public record Aberta(String refreshToken, UUID sessaoId) {
    }

    private final SessaoRepository sessoes;
    private final Duration validade;
    private final Duration graca;
    private final SessaoAtiva ativas;

    public SessaoService(SessaoRepository sessoes, SecurityProperties props, SessaoAtiva ativas) {
        this.sessoes = sessoes;
        this.validade = Duration.ofDays(props.refreshDias());
        this.graca = Duration.ofSeconds(props.refreshGraceSegundos());
        this.ativas = ativas;
    }

    public Duration getValidade() {
        return validade;
    }

    /** Abre uma sessão nova (nova família) e devolve o refresh token em claro. */
    @Transactional
    public Aberta abrir(UUID usuarioId, String userAgent) {
        UUID familia = UUID.randomUUID();
        return new Aberta(criar(usuarioId, familia, userAgent).bruto, familia);
    }

    @Transactional(noRollbackFor = SessaoInvalidaException.class)
    public Rotacao rotacionar(String bruto, String userAgent) {
        if (bruto == null || bruto.isBlank() || bruto.length() > 128) throw new SessaoInvalidaException();
        Sessao atual = sessoes.findByTokenHash(Hashes.sha256(bruto)).orElseThrow(SessaoInvalidaException::new);
        OffsetDateTime agora = OffsetDateTime.now();

        if (atual.getRevogadaEm() != null) {
            if (rotacaoConcorrente(atual, agora)) {
                // duas renovações simultâneas (ex.: duas instâncias do BFF): emite outro par na mesma família,
                // sem tocar no token já trocado. Vale só dentro da janela de tolerância.
                Criada fork = criar(atual.getUsuarioId(), atual.getFamilia(), userAgent);
                return new Rotacao(atual.getUsuarioId(), fork.bruto, atual.getFamilia());
            }
            // token já usado/revogado sendo apresentado de novo: possível roubo → derruba a família toda
            log.warn("Reuso de refresh token detectado; família revogada.");
            sessoes.revogarFamilia(atual.getFamilia(), agora);
            ativas.revogar(java.util.List.of(atual.getFamilia()));
            ativas.invalidarTodas();
            throw new SessaoInvalidaException();
        }
        if (atual.getExpiraEm().isBefore(agora)) throw new SessaoInvalidaException();

        Criada nova = criar(atual.getUsuarioId(), atual.getFamilia(), userAgent);
        atual.setRevogadaEm(agora);
        atual.setSubstituidaPor(nova.sessao.getId());
        return new Rotacao(atual.getUsuarioId(), nova.bruto, atual.getFamilia());
    }

    /** O token foi TROCADO (não revogado por logout) há pouco, e o sucessor segue vivo ou também foi trocado. */
    private boolean rotacaoConcorrente(Sessao atual, OffsetDateTime agora) {
        if (graca.isZero() || atual.getSubstituidaPor() == null) return false;
        if (atual.getRevogadaEm().plus(graca).isBefore(agora)) return false;
        return sessoes.findById(atual.getSubstituidaPor())
                .map(s -> s.getRevogadaEm() == null || s.getSubstituidaPor() != null)
                .orElse(false);
    }

    /** Logout: encerra a família do token (idempotente; token desconhecido é ignorado). */
    @Transactional
    public void encerrar(String bruto) {
        if (bruto == null || bruto.isBlank() || bruto.length() > 128) return;
        sessoes.findByTokenHash(Hashes.sha256(bruto))
                .ifPresent(s -> {
                    sessoes.revogarFamilia(s.getFamilia(), OffsetDateTime.now());
                    ativas.revogar(java.util.List.of(s.getFamilia()));
                });
        ativas.invalidarTodas();
    }

    /** Troca de senha, exclusão da conta ou "sair de todos os dispositivos". */
    @Transactional
    public void encerrarTodas(UUID usuarioId) {
        java.util.List<UUID> familias = sessoes.familiasAtivas(usuarioId);   // antes de revogar
        sessoes.revogarTodas(usuarioId, OffsetDateTime.now());
        ativas.revogar(familias);
        ativas.invalidarTodas();
    }

    @Transactional(readOnly = true)
    public long ativas(UUID usuarioId) {
        return sessoes.countByUsuarioIdAndRevogadaEmIsNullAndExpiraEmAfter(usuarioId, OffsetDateTime.now());
    }

    private record Criada(Sessao sessao, String bruto) {
    }

    private Criada criar(UUID usuarioId, UUID familia, String userAgent) {
        String bruto = Hashes.tokenAleatorio();
        Sessao s = new Sessao();
        s.setUsuarioId(usuarioId);
        s.setFamilia(familia);
        s.setTokenHash(Hashes.sha256(bruto));
        s.setExpiraEm(OffsetDateTime.now().plus(validade));
        s.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length())));
        return new Criada(sessoes.save(s), bruto);
    }
}
