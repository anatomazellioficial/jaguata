package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.dto.Requests.OngRequest;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.OngRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OngService {

    private final OngRepository ongs;

    public OngService(OngRepository ongs) {
        this.ongs = ongs;
    }

    /** Toda ONG nasce NÃO verificada; só ONGs verificadas participam do match. */
    @Transactional
    public Ong criar(OngRequest r) {
        if (ongs.existsByCnpj(r.cnpj())) {
            throw new RegraNegocioException("Já existe ONG cadastrada com este CNPJ.");
        }
        Ong o = new Ong();
        o.setCnpj(r.cnpj());
        o.setRazaoSocial(r.razaoSocial());
        o.setCnaePrincipal(r.cnaePrincipal());
        o.setNivelCapacidade(r.nivelCapacidade());
        o.setCep(r.cep());
        o.setLat(r.lat());
        o.setLng(r.lng());
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

    private Ong alterarVerificacao(UUID id, boolean valor) {
        Ong o = ongs.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("ONG", id));
        o.setVerified(valor);
        return o;
    }
}
