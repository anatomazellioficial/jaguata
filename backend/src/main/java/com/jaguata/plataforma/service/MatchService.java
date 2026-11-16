package com.jaguata.plataforma.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguata.plataforma.conta.AuditoriaService;
import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.CandidatoTag;
import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.Vaga;
import com.jaguata.plataforma.domain.enums.CategoriaTag;
import com.jaguata.plataforma.domain.enums.Escolaridade;
import com.jaguata.plataforma.domain.enums.GrupoPrioritario;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.RegimeContratacao;
import com.jaguata.plataforma.domain.enums.SetorAtuacao;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.CandidatoProximo;
import com.jaguata.plataforma.repository.CandidatoRepository;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import com.jaguata.plataforma.service.MatchScoreCalculator.Resultado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchService {

    /** Candidatos elegíveis considerados por busca (a lista final é cortada pelo score, não pela distância). */
    private static final int TETO_CANDIDATOS = 500;

    /**
     * Filtros opcionais da empresa na busca. Só critérios PROFISSIONAIS: idade, gênero e raça/cor não são
     * filtráveis (risco de discriminação); a diversidade entra pela vaga afirmativa, que só aumenta o score.
     */
    public record Filtros(ModeloTrabalho modelo, Escolaridade escolaridadeMinima,
                          Set<String> skillsObrigatorias, RegimeContratacao regime) {
        public static final Filtros VAZIO = new Filtros(null, null, Set.of(), null);
    }

    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final MatchRepository matches;
    private final MatchScoreCalculator score;
    private final AuditoriaService auditoria;
    private final ObjectMapper json;

    public MatchService(VagaRepository vagas, CandidatoRepository candidatos, MatchRepository matches,
                        MatchScoreCalculator score, AuditoriaService auditoria, ObjectMapper json) {
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.matches = matches;
        this.score = score;
        this.auditoria = auditoria;
        this.json = json;
    }

    @Transactional
    public List<Match> gerarSugestoes(UUID vagaId, double raioKm, int limite) {
        return gerarSugestoes(vagaId, raioKm, limite, Filtros.VAZIO);
    }

    /**
     * Motor de match: busca candidatos ELEGÍVEIS no raio (PostGIS), aplica os filtros da empresa, pontua cada
     * um nos 7 critérios e grava matches SUGERIDO dos melhores para os piores, cortando em {@code limite}.
     */
    @Transactional
    public List<Match> gerarSugestoes(UUID vagaId, double raioKm, int limite, Filtros filtros) {
        if (raioKm <= 0 || limite <= 0) {
            throw new RegraNegocioException("Raio e limite devem ser positivos.");
        }
        Vaga vaga = vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));

        double raioMetros = raioKm * 1000;
        List<CandidatoProximo> proximos = candidatos.buscarElegiveisParaVaga(vagaId, raioMetros,
                filtros.modelo() == null ? null : filtros.modelo().name(),
                filtros.escolaridadeMinima() == null ? null : filtros.escolaridadeMinima().name(),
                TETO_CANDIDATOS);

        Map<UUID, Candidato> porId = candidatos
                .findAllById(proximos.stream().map(CandidatoProximo::getCandidatoId).toList())
                .stream().collect(Collectors.toMap(Candidato::getId, Function.identity()));

        SetorAtuacao setorEmpresa = SetorAtuacao.de(vaga.getEmpresa().getCnaePrincipal());
        Set<String> skillsVaga = vaga.getSkills().stream().map(TextoUtil::chave).collect(Collectors.toSet());
        Set<String> obrigatorias = filtros.skillsObrigatorias().stream().map(TextoUtil::chave).collect(Collectors.toSet());

        record Candidatado(Match match, double distancia) {
        }

        List<Candidatado> avaliados = proximos.stream().map(p -> {
            Candidato c = porId.get(p.getCandidatoId());
            Set<String> skillsCandidato = skillsDe(c);
            if (!skillsCandidato.containsAll(obrigatorias)) return null;                       // filtro: skills obrigatórias
            if (filtros.regime() != null && !c.getRegimes().isEmpty() && !c.getRegimes().contains(filtros.regime())) {
                return null;                                                                   // filtro: regime
            }
            Resultado r = score.calcular(new MatchScoreCalculator.Entrada(
                    p.getDistanciaMetros(), raioMetros,
                    vaga.getModeloTrabalho(), c.getModeloTrabalhoPref(),
                    setorEmpresa, c.getOng().getSetores(),
                    vaga.getEscolaridadeMinima(), c.getEscolaridade(),
                    skillsVaga, skillsCandidato,
                    vaga.getRegime(), c.getRegimes(),
                    !vaga.getGruposPrioritarios().isEmpty(),
                    vaga.getGruposPrioritarios().stream().anyMatch(g -> g.contem(c))));
            Match m = new Match();
            m.setVaga(vaga);
            m.setCandidato(c);
            m.setOng(c.getOng());
            m.setStatus(StatusMatch.SUGERIDO);
            m.setMatchScore(r.total());
            m.setScoreDetalhe(serializar(r.motivos()));
            return new Candidatado(m, p.getDistanciaMetros());
        }).filter(java.util.Objects::nonNull).toList();

        List<Match> novos = avaliados.stream()
                .sorted(Comparator.<Candidatado, java.math.BigDecimal>comparing(a -> a.match().getMatchScore()).reversed()
                        .thenComparingDouble(Candidatado::distancia))
                .limit(limite)
                .map(Candidatado::match)
                .toList();

        return matches.saveAll(novos);
    }

    private static Set<String> skillsDe(Candidato c) {
        return c.getTags().stream()
                .filter(t -> t.getCategoria() == CategoriaTag.SKILL)
                .map(CandidatoTag::getTag).map(TextoUtil::chave).collect(Collectors.toSet());
    }

    private String serializar(List<Motivo> motivos) {
        try {
            return json.writeValueAsString(motivos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o detalhe do score", e);
        }
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
            auditoria.registrarComoAtual("MATCH_CONTRATADO", "match", m.getId(), "vaga preenchida");
            // contratou: a vaga é preenchida e as demais pendências dela são encerradas
            m.getVaga().setStatus(StatusVaga.PREENCHIDA);
            matches.encerrarPendenciasDaVaga(m.getVaga().getId(), m.getId(),
                    List.of(StatusMatch.SUGERIDO, StatusMatch.ENVIADO, StatusMatch.ENTREVISTA), StatusMatch.RECUSADO,
                    OffsetDateTime.now());
            return m;
        }

        m.setStatus(destino);
        auditoria.registrarComoAtual("MATCH_" + destino.name(), "match", m.getId(), null);
        return m;
    }
}
