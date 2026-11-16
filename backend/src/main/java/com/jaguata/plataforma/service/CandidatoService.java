package com.jaguata.plataforma.service;

import com.jaguata.plataforma.conta.AuditoriaService;
import com.jaguata.plataforma.conta.Termos;
import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.CandidatoTag;
import com.jaguata.plataforma.domain.Match;
import com.jaguata.plataforma.domain.Ong;
import com.jaguata.plataforma.domain.enums.CategoriaTag;
import com.jaguata.plataforma.domain.enums.Escolaridade;
import com.jaguata.plataforma.domain.enums.Genero;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.RacaCor;
import com.jaguata.plataforma.domain.enums.RegimeContratacao;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.dto.Responses.CandidatoEdicaoResponse;
import com.jaguata.plataforma.dto.Responses.CandidatoExportacaoResponse;
import com.jaguata.plataforma.dto.Responses.CandidatoResumoResponse;
import com.jaguata.plataforma.dto.Responses.ParticipacaoResponse;
import com.jaguata.plataforma.dto.Responses.TagResponse;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.CandidatoRepository;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.OngRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CandidatoService {

    /** Data neutra usada ao anonimizar (a restrição do banco exige 18+; esta data a satisfaz sem identificar ninguém). */
    private static final LocalDate DATA_ANONIMA = LocalDate.of(1900, 1, 1);

    public record TagInput(CategoriaTag categoria, String tag) {
    }

    public record CadastrarCandidatoCommand(
            String nomeCompleto,
            LocalDate dataNascimento,
            String cep,
            double lat,
            double lng,
            ModeloTrabalho modeloTrabalhoPref,
            List<TagInput> tags,
            Escolaridade escolaridade,
            Genero genero,
            RacaCor racaCor,
            Set<RegimeContratacao> regimes) {
    }

    private final CandidatoRepository candidatos;
    private final OngRepository ongs;
    private final MatchRepository matches;
    private final AuditoriaService auditoria;

    public CandidatoService(CandidatoRepository candidatos, OngRepository ongs, MatchRepository matches,
                            AuditoriaService auditoria) {
        this.candidatos = candidatos;
        this.ongs = ongs;
        this.matches = matches;
        this.auditoria = auditoria;
    }

    /**
     * @param usuarioQueAtesta usuário da ONG que confirmou ter o consentimento do(a) assistido(a); fica registrado
     *                         junto com a versão do termo e a data.
     */
    @Transactional
    public Candidato cadastrar(UUID ongId, CadastrarCandidatoCommand cmd, UUID usuarioQueAtesta) {
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
        c.setEscolaridade(cmd.escolaridade());
        c.setGenero(cmd.genero());          // autodeclaração opcional; nunca exibida às empresas
        c.setRacaCor(cmd.racaCor());
        c.setRegimes(cmd.regimes() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(cmd.regimes()));
        c.setConsentimentoEm(OffsetDateTime.now());
        c.setConsentimentoVersao(Termos.VERSAO);
        c.setConsentimentoUsuarioId(usuarioQueAtesta);
        if (cmd.tags() != null) {
            cmd.tags().forEach(t -> c.addTag(new CandidatoTag(t.categoria(), padronizar(t))));
        }
        Candidato salvo = candidatos.save(c);
        auditoria.registrar(usuarioQueAtesta, "ONG", "CANDIDATO_CADASTRADO", "candidato", salvo.getId(),
                "consentimento=" + Termos.VERSAO);
        return salvo;
    }

    @Transactional(readOnly = true)
    public CandidatoEdicaoResponse paraEdicao(UUID ongId, UUID candidatoId) {
        return CandidatoEdicaoResponse.from(daOng(ongId, candidatoId));
    }

    /** Habilidades (categoria SKILL) são gravadas com o nome padrão do catálogo, quando houver. */
    private static String padronizar(TagInput t) {
        return t.categoria() == CategoriaTag.SKILL ? java.util.Objects.requireNonNullElse(Skills.canonica(t.tag()), t.tag()) : t.tag();
    }

    /** Edição do cadastro. As sugestões ainda não enviadas deste candidato são descartadas (score velho). */
    @Transactional
    public Candidato atualizar(UUID ongId, UUID candidatoId, CadastrarCandidatoCommand cmd) {
        Candidato c = daOng(ongId, candidatoId);
        if (cmd.dataNascimento() == null
                || Candidato.calcularIdade(cmd.dataNascimento(), LocalDate.now()) < Candidato.IDADE_MINIMA) {
            throw new RegraNegocioException(
                    "Cadastro não permitido: o candidato deve ter " + Candidato.IDADE_MINIMA + " anos completos.");
        }
        c.setNomeCompleto(cmd.nomeCompleto());
        c.setDataNascimento(cmd.dataNascimento());
        c.setCep(cmd.cep());
        c.setLat(cmd.lat());
        c.setLng(cmd.lng());
        c.setModeloTrabalhoPref(cmd.modeloTrabalhoPref());
        c.setEscolaridade(cmd.escolaridade());
        c.setGenero(cmd.genero());
        c.setRacaCor(cmd.racaCor());
        c.getRegimes().clear();
        c.getTags().clear();
        // o Hibernate insere antes de apagar: sem este flush, regravar a MESMA tag/regime viola a unicidade
        candidatos.flush();
        if (cmd.regimes() != null) c.getRegimes().addAll(cmd.regimes());
        if (cmd.tags() != null) cmd.tags().forEach(t -> c.addTag(new CandidatoTag(t.categoria(), padronizar(t))));
        candidatos.saveAndFlush(c);
        int descartadas = matches.apagarDoCandidato(candidatoId, List.of(StatusMatch.SUGERIDO));
        auditoria.registrarComoAtual("CANDIDATO_ATUALIZADO", "candidato", candidatoId, "sugestoes_descartadas=" + descartadas);
        Candidato atual = candidatos.findById(candidatoId).orElseThrow();
        atual.getTags().size();       // coleções preguiçosas: inicializa dentro da transação (o controller mapeia depois)
        atual.getRegimes().size();
        return atual;
    }

    /** Candidatos ativos da ONG (anonimizados não aparecem), em ordem alfabética. */
    @Transactional(readOnly = true)
    public Page<CandidatoResumoResponse> listar(UUID ongId, int pagina, int tamanho) {
        PageRequest p = PageRequest.of(Math.max(0, pagina), Math.min(100, Math.max(1, tamanho)));
        return candidatos.findByOngIdAndAnonimizadoEmIsNullOrderByNomeCompletoAsc(ongId, p)
                .map(CandidatoResumoResponse::from);
    }

    /** Direito de acesso e portabilidade: todos os dados do candidato e as suas participações. */
    @Transactional
    public CandidatoExportacaoResponse exportar(UUID ongId, UUID candidatoId) {
        Candidato c = daOng(ongId, candidatoId);
        List<ParticipacaoResponse> participacoes = matches.findByCandidatoIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(m -> new ParticipacaoResponse(m.getVaga().getTituloExibicao(), m.getStatus(), m.getMatchScore(),
                        m.getCreatedAt(), m.getUpdatedAt()))
                .toList();
        auditoria.registrarComoAtual("CANDIDATO_EXPORTADO", "candidato", c.getId(), null);
        return new CandidatoExportacaoResponse(c.getId(), c.getOng().getId(), c.getNomeCompleto(), c.getDataNascimento(),
                c.getCep(), c.getLat(), c.getLng(), c.getModeloTrabalhoPref(),
                c.getEscolaridade(), c.getGenero(), c.getRacaCor(), new java.util.ArrayList<>(c.getRegimes()),
                c.getTags().stream().map(t -> new TagResponse(t.getCategoria(), t.getTag())).toList(),
                c.getCreatedAt(), c.getConsentimentoEm(), c.getConsentimentoVersao(), participacoes);
    }

    /**
     * Direito de eliminação: remove os dados pessoais (nome, nascimento, CEP/coordenadas e tags) e encerra as
     * pendências abertas. O registro das contratações já concluídas permanece, sem identificar a pessoa.
     */
    @Transactional
    public void anonimizar(UUID ongId, UUID candidatoId) {
        Candidato c = daOng(ongId, candidatoId);
        c.setNomeCompleto("Candidato removido");
        c.setDataNascimento(DATA_ANONIMA);
        c.setGenero(null);
        c.setRacaCor(null);
        c.setEscolaridade(null);
        c.getRegimes().clear();
        c.setCep("00000000");
        c.setLat(0);
        c.setLng(0);
        c.getTags().clear();
        c.setConsentimentoUsuarioId(null);
        c.setAnonimizadoEm(OffsetDateTime.now());
        candidatos.saveAndFlush(c);
        matches.encerrarPendenciasDoCandidato(c.getId(),
                List.of(StatusMatch.SUGERIDO, StatusMatch.ENVIADO, StatusMatch.ENTREVISTA), StatusMatch.RECUSADO,
                OffsetDateTime.now());
        auditoria.registrarComoAtual("CANDIDATO_ANONIMIZADO", "candidato", candidatoId, null);
    }

    /**
     * Exclusão DEFINITIVA: apaga o cadastro, as tags e todos os matches. Só é permitida se a pessoa nunca foi
     * contratada; com contratação, o histórico precisa ser preservado e o caminho é a anonimização.
     */
    @Transactional
    public void excluirDefinitivamente(UUID ongId, UUID candidatoId) {
        Candidato c = daOng(ongId, candidatoId);
        if (matches.existsByCandidatoIdAndStatus(c.getId(), StatusMatch.CONTRATADO)) {
            throw new RegraNegocioException("Este(a) candidato(a) tem uma contratação registrada. Para preservar o histórico, "
                    + "use a anonimização em vez da exclusão definitiva.");
        }
        matches.apagarDoCandidato(c.getId(), List.of(StatusMatch.values()));
        auditoria.registrarComoAtual("CANDIDATO_EXCLUIDO", "candidato", candidatoId, null);
        candidatos.deleteById(candidatoId);
        candidatos.flush();
    }

    /** 404 (e não 403) para candidato de outra ONG ou já anonimizado: não revela a existência. */
    private Candidato daOng(UUID ongId, UUID candidatoId) {
        Candidato c = candidatos.findById(candidatoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Candidato", candidatoId));
        if (!c.getOng().getId().equals(ongId) || c.isAnonimizado()) {
            throw new RecursoNaoEncontradoException("Candidato", candidatoId);
        }
        return c;
    }
}
