package com.jaguata.plataforma.conta;

import com.jaguata.plataforma.domain.Auditoria;
import com.jaguata.plataforma.repository.AuditoriaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Trilha de auditoria de ações sensíveis. Regras: nunca gravar dados pessoais no detalhe (nomes, e-mails,
 * CNPJ de pessoa física, conteúdo de candidatos) e nunca falhar a operação principal por causa do log.
 */
@Service
public class AuditoriaService {

    private final AuditoriaRepository repo;

    public AuditoriaService(AuditoriaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void registrar(UUID usuarioId, String papel, String acao, String entidade, UUID entidadeId, String detalhe) {
        Auditoria a = new Auditoria();
        a.setUsuarioId(usuarioId);
        a.setPapel(papel);
        a.setAcao(acao);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId);
        a.setDetalhe(detalhe == null ? null : detalhe.substring(0, Math.min(300, detalhe.length())));
        repo.save(a);
    }

    /** Registra com o usuário autenticado na requisição atual (se houver). */
    @Transactional
    public void registrarComoAtual(String acao, String entidade, UUID entidadeId, String detalhe) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            registrar(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("papel"), acao, entidade, entidadeId, detalhe);
        } else {
            registrar(null, null, acao, entidade, entidadeId, detalhe);
        }
    }

    @Transactional(readOnly = true)
    public Page<Auditoria> listar(String acao, int pagina, int tamanho) {
        PageRequest p = PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanho)));
        return acao == null || acao.isBlank() ? repo.findAllByOrderByQuandoDesc(p) : repo.findByAcaoOrderByQuandoDesc(acao, p);
    }
}
