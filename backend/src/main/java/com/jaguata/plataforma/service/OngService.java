package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.enums.NivelCapacidade;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.integracao.DadosCnpj;
import com.jaguata.plataforma.integracao.Localizacao;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.OngRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OngService {

    private final OngRepository ongs;
    private final com.jaguata.plataforma.conta.AuditoriaService auditoria;

    public OngService(OngRepository ongs, com.jaguata.plataforma.conta.AuditoriaService auditoria) {
        this.ongs = ongs;
        this.auditoria = auditoria;
    }

    public boolean cnpjCadastrado(String cnpj) {
        return ongs.existsByCnpj(cnpj);
    }

    /**
     * Toda ONG nasce NÃO verificada; só ONGs verificadas participam do match. Razão social e CNAE vêm da
     * Receita Federal e as coordenadas, do CEP da sede.
     */
    @Transactional
    public Ong criar(DadosCnpj d, Localizacao loc, NivelCapacidade nivel) {
        if (ongs.existsByCnpj(d.cnpj())) {
            throw new RegraNegocioException("Já existe ONG cadastrada com este CNPJ.");
        }
        Ong o = new Ong();
        o.setCnpj(d.cnpj());
        o.setRazaoSocial(d.razaoSocial());
        o.setCnaePrincipal(d.cnaePrincipal());
        o.setNivelCapacidade(nivel);
        o.setCep(loc.cep());
        o.setLat(loc.lat());
        o.setLng(loc.lng());
        o.setVerified(false);
        return ongs.save(o);
    }

    @Transactional
    public Ong verificar(UUID id) {
        return alterarVerificacao(id, true);
    }

    /** Retira a verificação: a ONG deixa de entrar em novas sugestões de match. */
    @Transactional
    public Ong revogarVerificacao(UUID id) {
        return alterarVerificacao(id, false);
    }

    /** @param verificada null = todas; true/false = filtra. Mais recentes primeiro. */
    @Transactional(readOnly = true)
    public java.util.List<Ong> listar(Boolean verificada) {
        return verificada == null ? ongs.findAllByOrderByCreatedAtDesc()
                : ongs.findByVerifiedOrderByCreatedAtDesc(verificada);
    }

    @Transactional(readOnly = true)
    public java.util.Set<com.jaguata.plataforma.domain.enums.SetorAtuacao> setores(UUID id) {
        Ong o = ongs.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("ONG", id));
        return new java.util.TreeSet<>(o.getSetores());
    }

    @Transactional
    public java.util.Set<com.jaguata.plataforma.domain.enums.SetorAtuacao> atualizarSetores(
            UUID id, java.util.Set<com.jaguata.plataforma.domain.enums.SetorAtuacao> novos) {
        Ong o = ongs.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("ONG", id));
        o.getSetores().clear();
        o.getSetores().addAll(novos);
        auditoria.registrarComoAtual("ONG_SETORES_ATUALIZADOS", "ong", id, "total=" + novos.size());
        return new java.util.TreeSet<>(o.getSetores());
    }

    private Ong alterarVerificacao(UUID id, boolean valor) {
        Ong o = ongs.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("ONG", id));
        o.setVerified(valor);
        auditoria.registrarComoAtual(valor ? "ONG_VERIFICADA" : "ONG_VERIFICACAO_REVOGADA", "ong", id, null);
        return o;
    }
}
