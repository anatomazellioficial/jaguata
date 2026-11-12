package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.Vaga;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.CandidatoProximo;
import com.jaguata.plataforma.repository.CandidatoRepository;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final MatchRepository matches;
    private final MatchScoreCalculator score;
    private final CnaeCompatibilidade cnae;

    public MatchService(VagaRepository vagas, CandidatoRepository candidatos, MatchRepository matches,
                        MatchScoreCalculator score, CnaeCompatibilidade cnae) {
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.matches = matches;
        this.score = score;
        this.cnae = cnae;
    }

    /**
     * Motor de match: busca candidatos elegíveis no raio (PostGIS), pontua cada um
     * (território + modelo de trabalho + CNAE) e grava matches SUGERIDO, do melhor score ao pior.
     */
    @Transactional
    public List<Match> gerarSugestoes(UUID vagaId, double raioKm, int limite) {
        if (raioKm <= 0 || limite <= 0) {
            throw new RegraNegocioException("Raio e limite devem ser positivos.");
        }
        Vaga vaga = vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));

        double raioMetros = raioKm * 1000;
        List<CandidatoProximo> proximos = candidatos.buscarElegiveisParaVaga(vagaId, raioMetros, limite);

        Map<UUID, Candidato> porId = candidatos
                .findAllById(proximos.stream().map(CandidatoProximo::getCandidatoId).toList())
                .stream().collect(Collectors.toMap(Candidato::getId, Function.identity()));

        List<Match> novos = proximos.stream().map(p -> {
            Candidato c = porId.get(p.getCandidatoId());
            Match m = new Match();
            m.setVaga(vaga);
            m.setCandidato(c);
            m.setOng(c.getOng());
            m.setStatus(StatusMatch.SUGERIDO);
            m.setMatchScore(score.calcular(
                    p.getDistanciaMetros(), raioMetros,
                    vaga.getModeloTrabalho(), c.getModeloTrabalhoPref(),
                    cnae.compativel(vaga.getEmpresa().getCnaePrincipal(), c.getOng().getCnaePrincipal())));
            return m;
        }).sorted((a, b) -> b.getMatchScore().compareTo(a.getMatchScore())).toList();

        return matches.saveAll(novos);
    }

    @Transactional(readOnly = true)
    public List<Match> listarPorVaga(UUID vagaId) {
        if (!vagas.existsById(vagaId)) {
            throw new RecursoNaoEncontradoException("Vaga", vagaId);
        }
        return matches.findByVagaIdOrderByMatchScoreDesc(vagaId);
    }

    @Transactional(readOnly = true)
    public List<Match> listarPorOng(UUID ongId, StatusMatch status) {
        return matches.findByOngIdAndStatus(ongId, status);
    }

    /** Avança o funil respeitando as transições válidas. */
    @Transactional
    public Match avancar(UUID matchId, StatusMatch destino) {
        Match m = matches.findDetalhadoById(matchId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Match", matchId));
        if (!m.getStatus().podeTransitarPara(destino)) {
            throw new RegraNegocioException(
                    "Transição inválida do funil: " + m.getStatus() + " -> " + destino);
        }

        // só se avança numa vaga aberta (recusar é sempre permitido)
        boolean avanca = destino == StatusMatch.ENVIADO || destino == StatusMatch.ENTREVISTA
                || destino == StatusMatch.CONTRATADO;
        if (avanca && m.getVaga().getStatus() != StatusVaga.ABERTA) {
            throw new RegraNegocioException("A vaga não está aberta: não é possível avançar este candidato.");
        }

        if (destino == StatusMatch.CONTRATADO) {
            if (matches.existsByCandidatoIdAndStatus(m.getCandidato().getId(), StatusMatch.CONTRATADO)) {
                throw new RegraNegocioException("Este candidato já foi contratado.");
            }
            m.setStatus(destino);
            // contratou: a vaga é preenchida e as demais pendências dela são encerradas
            m.getVaga().setStatus(StatusVaga.PREENCHIDA);
            matches.encerrarPendenciasDaVaga(m.getVaga().getId(), m.getId(),
                    List.of(StatusMatch.SUGERIDO, StatusMatch.ENVIADO, StatusMatch.ENTREVISTA), StatusMatch.RECUSADO,
                    OffsetDateTime.now());
            return m;
        }

        m.setStatus(destino);
        return m;
    }
}
