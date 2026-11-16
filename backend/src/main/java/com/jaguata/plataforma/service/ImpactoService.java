package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.enums.SetorAtuacao;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.repository.CandidatoRepository;
import com.jaguata.plataforma.repository.EmpresaRepository;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.OngRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Relatórios de impacto (ESG) da empresa e da ONG.
 * <p>
 * Cuidado deliberado com dados de grupos: os relatórios trazem <b>só totais</b> (vagas, funil, contratações, tempo
 * médio, parceiros, setores). Não há quebra por gênero, raça/cor, deficiência ou qualquer atributo do(a)
 * candidato(a): com poucas contratações, qualquer recorte desses reidentificaria pessoas. A única referência à
 * diversidade é o número de contratações em vagas que a PRÓPRIA empresa marcou como afirmativas (uma escolha dela,
 * sem dizer quais grupos nem quem).
 */
@Service
public class ImpactoService {

    public record PorOng(String ong, int contratacoes) {
    }

    public record PorSetor(String setor, int contratacoes) {
    }

    public record EmpresaImpacto(OffsetDateTime geradoEm, int vagasTotal, int vagasAbertas, int vagasPreenchidas,
                                 int vagasPausadasOuEncerradas, int sugestoes, int candidatosEnviados, int entrevistas,
                                 int contratacoes, int ongsParceiras, List<PorOng> contratacoesPorOng,
                                 Double tempoMedioAteContratacaoDias, int vagasAfirmativas,
                                 int contratacoesEmVagasAfirmativas) {
    }

    public record OngImpacto(OffsetDateTime geradoEm, int candidatosAtivos, int aguardandoEnvio, int emAnalise,
                             int emEntrevista, int contratacoes, int recusas, Double taxaColocacaoPct,
                             Double tempoMedioAteContratacaoDias, int empresasParceiras,
                             List<PorSetor> contratacoesPorSetor) {
    }

    private final MatchRepository matches;
    private final VagaRepository vagas;
    private final CandidatoRepository candidatos;
    private final EmpresaRepository empresas;
    private final OngRepository ongs;

    public ImpactoService(MatchRepository matches, VagaRepository vagas, CandidatoRepository candidatos,
                          EmpresaRepository empresas, OngRepository ongs) {
        this.matches = matches;
        this.vagas = vagas;
        this.candidatos = candidatos;
        this.empresas = empresas;
        this.ongs = ongs;
    }

    @Transactional(readOnly = true)
    public EmpresaImpacto daEmpresa(UUID empresaId) {
        if (!empresas.existsById(empresaId)) throw new RecursoNaoEncontradoException("Empresa", empresaId);
        var minhasVagas = vagas.findByEmpresaId(empresaId);
        List<Match> ms = matches.doEmpresa(empresaId);
        List<Match> contratados = ms.stream().filter(m -> m.getStatus() == StatusMatch.CONTRATADO).toList();

        Map<String, Integer> porOng = new LinkedHashMap<>();
        contratados.forEach(m -> porOng.merge(m.getOng().getRazaoSocial(), 1, Integer::sum));
        List<PorOng> listaOng = porOng.entrySet().stream()
                .map(e -> new PorOng(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(PorOng::contratacoes).reversed().thenComparing(PorOng::ong)).toList();

        Set<UUID> vagasAfirmativas = minhasVagas.stream()
                .filter(v -> !v.getGruposPrioritarios().isEmpty()).map(v -> v.getId()).collect(Collectors.toSet());

        return new EmpresaImpacto(OffsetDateTime.now(),
                minhasVagas.size(),
                (int) minhasVagas.stream().filter(v -> v.getStatus() == StatusVaga.ABERTA).count(),
                (int) minhasVagas.stream().filter(v -> v.getStatus() == StatusVaga.PREENCHIDA).count(),
                (int) minhasVagas.stream().filter(v -> v.getStatus() == StatusVaga.PAUSADA || v.getStatus() == StatusVaga.ENCERRADA).count(),
                ms.size(),
                (int) ms.stream().filter(m -> m.getStatus() == StatusMatch.ENVIADO || m.getStatus() == StatusMatch.ENTREVISTA
                        || m.getStatus() == StatusMatch.CONTRATADO).count(),
                (int) ms.stream().filter(m -> m.getStatus() == StatusMatch.ENTREVISTA || m.getStatus() == StatusMatch.CONTRATADO).count(),
                contratados.size(),
                // parceira = ONG que de fato enviou candidatos (não basta ter recebido uma sugestão)
                (int) ms.stream().filter(m -> m.getStatus() == StatusMatch.ENVIADO || m.getStatus() == StatusMatch.ENTREVISTA
                        || m.getStatus() == StatusMatch.CONTRATADO).map(m -> m.getOng().getId()).distinct().count(),
                listaOng,
                mediaDias(contratados.stream().map(m -> Duration.between(m.getVaga().getCreatedAt(), m.getUpdatedAt())).toList()),
                vagasAfirmativas.size(),
                (int) contratados.stream().filter(m -> vagasAfirmativas.contains(m.getVaga().getId())).count());
    }

    @Transactional(readOnly = true)
    public OngImpacto daOng(UUID ongId) {
        if (!ongs.existsById(ongId)) throw new RecursoNaoEncontradoException("ONG", ongId);
        List<Match> ms = matches.daOng(ongId);
        List<Match> contratados = ms.stream().filter(m -> m.getStatus() == StatusMatch.CONTRATADO).toList();
        long ativos = candidatos.countByOngIdAndAnonimizadoEmIsNull(ongId);

        Map<String, Integer> porSetor = new LinkedHashMap<>();
        for (Match m : contratados) {
            SetorAtuacao s = SetorAtuacao.de(m.getVaga().getEmpresa().getCnaePrincipal());
            porSetor.merge(s == null ? "Não classificado" : s.getRotulo(), 1, Integer::sum);
        }
        List<PorSetor> listaSetor = porSetor.entrySet().stream()
                .map(e -> new PorSetor(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(PorSetor::contratacoes).reversed().thenComparing(PorSetor::setor)).toList();

        long contratadosDistintos = contratados.stream().map(m -> m.getCandidato().getId()).distinct().count();
        return new OngImpacto(OffsetDateTime.now(), (int) ativos,
                contar(ms, StatusMatch.SUGERIDO), contar(ms, StatusMatch.ENVIADO), contar(ms, StatusMatch.ENTREVISTA),
                contratados.size(), contar(ms, StatusMatch.RECUSADO),
                ativos == 0 ? null : Math.round(1000.0 * contratadosDistintos / ativos) / 10.0,
                mediaDias(contratados.stream().map(m -> Duration.between(m.getCreatedAt(), m.getUpdatedAt())).toList()),
                (int) contratados.stream().map(m -> m.getVaga().getEmpresa().getId()).distinct().count(),
                listaSetor);
    }

    private static int contar(List<Match> ms, StatusMatch s) {
        return (int) ms.stream().filter(m -> m.getStatus() == s).count();
    }

    private static Double mediaDias(List<Duration> duracoes) {
        if (duracoes.isEmpty()) return null;
        double dias = duracoes.stream().mapToLong(Duration::toSeconds).average().orElse(0) / 86_400.0;
        return Math.round(dias * 10) / 10.0;
    }
}
