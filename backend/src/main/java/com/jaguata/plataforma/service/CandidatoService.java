package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.CandidatoTag;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.enums.CategoriaTag;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.CandidatoRepository;
import com.jaguata.plataforma.repository.OngRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CandidatoService {

    public record TagInput(CategoriaTag categoria, String tag) {
    }

    public record CadastrarCandidatoCommand(
            String nomeCompleto,
            LocalDate dataNascimento,
            String cep,
            double lat,
            double lng,
            ModeloTrabalho modeloTrabalhoPref,
            List<TagInput> tags) {
    }

    private final CandidatoRepository candidatos;
    private final OngRepository ongs;

    public CandidatoService(CandidatoRepository candidatos, OngRepository ongs) {
        this.candidatos = candidatos;
        this.ongs = ongs;
    }

    @Transactional
    public Candidato cadastrar(UUID ongId, CadastrarCandidatoCommand cmd) {
        Ong ong = ongs.findById(ongId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ONG", ongId));

        if (cmd.dataNascimento() == null
                || Candidato.calcularIdade(cmd.dataNascimento(), LocalDate.now()) < Candidato.IDADE_MINIMA) {
            throw new RegraNegocioException(
                    "Cadastro não permitido: o candidato deve ter " + Candidato.IDADE_MINIMA + " anos completos.");
        }

        Candidato c = new Candidato();
        c.setOng(ong);
        c.setNomeCompleto(cmd.nomeCompleto());
        c.setDataNascimento(cmd.dataNascimento());
        c.setCep(cmd.cep());
        c.setLat(cmd.lat());
        c.setLng(cmd.lng());
        c.setModeloTrabalhoPref(cmd.modeloTrabalhoPref());
        if (cmd.tags() != null) {
            cmd.tags().forEach(t -> c.addTag(new CandidatoTag(t.categoria(), t.tag())));
        }
        return candidatos.save(c);
    }
}
