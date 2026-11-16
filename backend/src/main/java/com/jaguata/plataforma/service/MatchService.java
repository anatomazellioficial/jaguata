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
                          Set<String> skillsObrigatorias, RegimeContratacao regime,
                          Integer idadeMin, Integer idadeMax) {
        public static final Filtros VAZIO = new Filtros(null, null, Set.of(), null, null, null);

        public Filtros(ModeloTrabalho modelo, Escolaridade escolaridadeMinima, Set<String> skillsObrigatorias,
                       RegimeContratacao regime) {
            this(modelo, escolaridadeMinima, skillsObrigatorias, regime, null, null);
        }
    }

    /**
     * Opções da distribuição: {@code distribuirPorOng} reparte as sugestões entre as ONGs (uma de cada vez, da
     * melhor para a pior de cada uma) em vez de deixar a ONG com mais candidatos bem pontuados ficar com todas;
     * {@code excluirCandidatos} tira da busca quem já foi alocado a outra vaga do mesmo lote.
     */
    public record Opcoes(boolean distribuirPorOng, Set<UUID> excluirCandidatos) {
        public static final Opcoes PADRAO = new Opcoes(false, Set.of());
    }

    public record ResultadoLote(UUID vagaId, String tituloVaga, int sugestoes) {
    }

    public static final int MAX_VAGAS_LOTE = 50;

    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final MatchRepository matches;
    private final MatchScoreCalculator score;
    private final AuditoriaService auditoria;
    private final ObjectMapper json;
    private final NotificacaoService notificacoes;
    private final boolean filtroIdadeLigado;

    /** O filtro de idade existe, mas só funciona se o jurídico aprovar e a configuração for ligada. */
    public boolean filtroIdadeLigado() {
        return filtroIdadeLigado;
    }

    public MatchService(VagaRepository vagas, CandidatoRepository candidatos, MatchRepository matches,
                        MatchScoreCalculator score, AuditoriaService auditoria, ObjectMapper json,
                        NotificacaoService notificacoes,
                        @org.springframework.beans.factory.annotation.Value("${app.matching.filtro-idade:false}") boolean filtroIdadeLigado) {
        this.notificacoes = notificacoes;
        this.filtroIdadeLigado = filtroIdadeLigado;
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
        return gerarSugestoes(vagaId, raioKm, limite, filtros, Opcoes.PADRAO);
    }

    @Transactional
    public List<Match> gerarSugestoes(UUID vagaId, double raioKm, int limite, Filtros filtros, Opcoes opcoes) {
        if (raioKm <= 0 || limite <= 0) {
            throw new RegraNegocioException("Raio e limite devem ser positivos.");
        }
        validarIdade(filtros);
        Vaga vaga = vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));

        double raioMetros = raioKm * 1000;
        List<CandidatoProximo> proximos = candidatos.buscarElegiveisParaVaga(vagaId, raioMetros,
                filtros.modelo() == null ? null : filtros.modelo().name(),
                filtros.escolaridadeMinima() == null ? null : filtros.escolaridadeMinima().name(),
                filtros.idadeMin() == null ? null : java.time.LocalDate.now().minusYears(filtros.idadeMin()),
                filtros.idadeMax() == null ? null : java.time.LocalDate.now().minusYears(filtros.idadeMax() + 1L).plusDays(1),
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
            if (opcoes.excluirCandidatos().contains(p.getCandidatoId())) return null;          // já alocado no lote
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

        List<Candidatado> ordenados = avaliados.stream()
                .sorted(Comparator.<Candidatado, java.math.BigDecimal>comparing(x -> x.match().getMatchScore()).reversed()
                        .thenComparingDouble(Candidatado::distancia))
                .toList();
        List<Match> novos = new java.util.ArrayList<>();
        if (opcoes.distribuirPorOng()) {
            // uma ONG de cada vez (a que tem o melhor candidato primeiro), sempre o melhor que ainda resta dela
            Map<UUID, java.util.ArrayDeque<Match>> filas = new java.util.LinkedHashMap<>();
            for (Candidatado x : ordenados) {
                filas.computeIfAbsent(x.match().getOng().getId(), k -> new java.util.ArrayDeque<>()).add(x.match());
            }
            while (novos.size() < limite && !filas.isEmpty()) {
                java.util.Iterator<java.util.ArrayDeque<Match>> it = filas.values().iterator();
                while (it.hasNext() && novos.size() < limite) {
                    java.util.ArrayDeque<Match> fila = it.next();
                    novos.add(fila.poll());
                    if (fila.isEmpty()) it.remove();
                }
            }
        } else {
            ordenados.stream().limit(limite).forEach(x -> novos.add(x.match()));
        }

        List<Match> salvos = matches.saveAll(novos);
        // um aviso por ONG (com a quantidade), nunca por candidato e sem nomes
        salvos.stream().collect(Collectors.groupingBy(m -> m.getOng().getId(), Collectors.counting()))
                .forEach((ongId, qtd) -> notificacoes.novasSugestoes(ongId, vaga.getTituloExibicao(), qtd.intValue()));
        return salvos;
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

    private void validarIdade(Filtros f) {
        if (f.idadeMin() == null && f.idadeMax() == null) return;
        if (!filtroIdadeLigado) {
            throw new RegraNegocioException("O filtro de idade está desabilitado nesta instalação.");
        }
        int min = f.idadeMin() == null ? 18 : f.idadeMin();
        int max = f.idadeMax() == null ? 120 : f.idadeMax();
        if (min < 18 || max > 120 || min > max) {
            throw new RegraNegocioException("Faixa de idade inválida (entre 18 e 120 anos, com mínimo menor ou igual ao máximo).");
        }
    }

    /**
     * Busca em lote: roda o match para todas as vagas ABERTAS da empresa, da mais antiga à mais nova. Cada candidato
     * é sugerido a UMA só vaga do lote (as seguintes o excluem), e com {@code distribuir} as sugestões de cada vaga
     * são repartidas entre as ONGs. Evita que 10 vagas iguais recebam os mesmos 10 candidatos.
     */
    @Transactional
    public List<ResultadoLote> gerarSugestoesEmLote(UUID empresaId, double raioKm, int limitePorVaga, Filtros filtros,
                                                    boolean distribuir) {
        List<Vaga> abertas = vagas.findByEmpresaId(empresaId).stream()
                .filter(v -> v.getStatus() == StatusVaga.ABERTA)
                .sorted(Comparator.comparing(Vaga::getCreatedAt)).toList();
        if (abertas.isEmpty()) throw new RegraNegocioException("Não há vagas abertas para buscar em lote.");
        if (abertas.size() > MAX_VAGAS_LOTE) {
            throw new RegraNegocioException("No máximo " + MAX_VAGAS_LOTE + " vagas abertas por busca em lote.");
        }
        Set<UUID> alocados = new java.util.HashSet<>();
        List<ResultadoLote> out = new java.util.ArrayList<>();
        for (Vaga v : abertas) {
            List<Match> novos = gerarSugestoes(v.getId(), raioKm, limitePorVaga, filtros,
                    new Opcoes(distribuir, Set.copyOf(alocados)));
            novos.forEach(m -> alocados.add(m.getCandidato().getId()));
            out.add(new ResultadoLote(v.getId(), v.getTituloExibicao(), novos.size()));
        }
        return out;
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
            notificacoes.contratacao(m.getOng().getId(), m.getVaga().getTituloExibicao());
            matches.encerrarPendenciasDaVaga(m.getVaga().getId(), m.getId(),
                    List.of(StatusMatch.SUGERIDO, StatusMatch.ENVIADO, StatusMatch.ENTREVISTA), StatusMatch.RECUSADO,
                    OffsetDateTime.now());
            return m;
        }

        m.setStatus(destino);
        auditoria.registrarComoAtual("MATCH_" + destino.name(), "match", m.getId(), null);
        if (destino == StatusMatch.ENVIADO) {
            notificacoes.candidatoEnviado(m.getVaga().getEmpresa().getId(), m.getVaga().getTituloExibicao());
        } else if (destino == StatusMatch.ENTREVISTA) {
            notificacoes.convocacao(m.getOng().getId(), m.getVaga().getTituloExibicao());
        }
        return m;
    }
}
