package com.jaguata.plataforma.service;

import com.jaguata.plataforma.conta.AuditoriaService;
import com.jaguata.plataforma.domain.Empresa;
import com.jaguata.plataforma.domain.Vaga;
import com.jaguata.plataforma.domain.enums.Escolaridade;
import com.jaguata.plataforma.domain.enums.GrupoPrioritario;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.RegimeContratacao;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.domain.enums.StatusVaga;
import com.jaguata.plataforma.domain.enums.TipoContrato;
import com.jaguata.plataforma.exception.RecursoNaoEncontradoException;
import com.jaguata.plataforma.exception.RegraNegocioException;
import com.jaguata.plataforma.repository.EmpresaRepository;
import com.jaguata.plataforma.repository.MatchRepository;
import com.jaguata.plataforma.repository.VagaRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VagaService {

    public static final int MAX_SKILLS = 15;
    public static final int MIN_JUSTIFICATIVA = 20;

    public record CriarVagaCommand(
            @NotNull NivelCargo nivelCargo,
            @NotNull TipoContrato tipoContrato,
            @NotNull ModeloTrabalho modeloTrabalho,
            @NotNull @Pattern(regexp = "\\d{8}") String cepAtuacao,
            double lat,
            double lng,
            Escolaridade escolaridadeMinima,
            RegimeContratacao regime,
            List<String> skills,
            Set<GrupoPrioritario> gruposPrioritarios,
            String justificativaAfirmativa) {

        /** Vaga sem requisitos adicionais (atalho usado em testes e integrações simples). */
        public CriarVagaCommand(NivelCargo nivelCargo, TipoContrato tipoContrato, ModeloTrabalho modeloTrabalho,
                                String cepAtuacao, double lat, double lng) {
            this(nivelCargo, tipoContrato, modeloTrabalho, cepAtuacao, lat, lng, null, null, null, null, null);
        }
    }

    private final VagaRepository vagas;
    private final EmpresaRepository empresas;
    private final AuditoriaService auditoria;
    private final MatchRepository matches;

    public VagaService(VagaRepository vagas, EmpresaRepository empresas, AuditoriaService auditoria,
                       MatchRepository matches) {
        this.matches = matches;
        this.vagas = vagas;
        this.empresas = empresas;
        this.auditoria = auditoria;
    }

    /**
     * O cargo vem do enum (cardápio fechado). O título exibido à ONG é derivado do porte da empresa pela
     * Matriz de Complexidade (callback de Vaga). Vaga afirmativa (com grupos prioritários) exige justificativa
     * e fica registrada na auditoria.
     */
    @Transactional
    public Vaga criar(UUID empresaId, CriarVagaCommand cmd) {
        Empresa empresa = empresas.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa", empresaId));

        Vaga vaga = new Vaga();
        vaga.setEmpresa(empresa);
        vaga.setStatus(StatusVaga.ABERTA);
        Set<GrupoPrioritario> grupos = preencher(vaga, cmd);
        Vaga salva = vagas.save(vaga);

        if (!grupos.isEmpty()) {
            auditoria.registrarComoAtual("VAGA_AFIRMATIVA", "vaga", salva.getId(),
                    "grupos=" + grupos.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        }
        return salva;
    }

    /** Valida e copia os campos editáveis do comando para a vaga; devolve os grupos priorizados. */
    private Set<GrupoPrioritario> preencher(Vaga vaga, CriarVagaCommand cmd) {
        Set<GrupoPrioritario> grupos = cmd.gruposPrioritarios() == null ? Set.of() : cmd.gruposPrioritarios();
        String justificativa = cmd.justificativaAfirmativa() == null ? null : cmd.justificativaAfirmativa().trim();
        if (!grupos.isEmpty() && (justificativa == null || justificativa.length() < MIN_JUSTIFICATIVA)) {
            throw new RegraNegocioException("Vaga afirmativa exige uma justificativa da ação afirmativa (mínimo de "
                    + MIN_JUSTIFICATIVA + " caracteres).");
        }
        vaga.setNivelCargo(cmd.nivelCargo());
        vaga.setTipoContrato(cmd.tipoContrato());
        vaga.setModeloTrabalho(cmd.modeloTrabalho());
        vaga.setCepAtuacao(cmd.cepAtuacao());
        vaga.setLat(cmd.lat());
        vaga.setLng(cmd.lng());
        vaga.setEscolaridadeMinima(cmd.escolaridadeMinima());
        vaga.setRegime(cmd.regime());
        vaga.setSkills(normalizarSkills(cmd.skills()));
        vaga.setGruposPrioritarios(new LinkedHashSet<>(grupos));
        vaga.setJustificativaAfirmativa(grupos.isEmpty() ? null : justificativa);
        return grupos;
    }

    /**
     * Edita a vaga (aberta ou pausada). As sugestões ainda não enviadas são descartadas, porque o score delas
     * foi calculado com os requisitos antigos; é só buscar de novo. Com candidatos já enviados ou em entrevista,
     * a edição é recusada para não mudar as regras no meio do processo.
     */
    @Transactional
    public Vaga atualizar(UUID vagaId, CriarVagaCommand cmd) {
        Vaga vaga = vagas.findById(vagaId).orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
        if (vaga.getStatus() != StatusVaga.ABERTA && vaga.getStatus() != StatusVaga.PAUSADA) {
            throw new RegraNegocioException("Só é possível editar vagas abertas ou pausadas.");
        }
        if (matches.existsByVagaIdAndStatusIn(vagaId, List.of(StatusMatch.ENVIADO, StatusMatch.ENTREVISTA))) {
            throw new RegraNegocioException("Há candidatos enviados ou em entrevista: conclua ou recuse antes de editar a vaga.");
        }
        int descartadas = matches.apagarDaVaga(vagaId, List.of(StatusMatch.SUGERIDO));
        vaga = vagas.findById(vagaId).orElseThrow();   // o delete em lote limpou o contexto
        Set<GrupoPrioritario> grupos = preencher(vaga, cmd);
        auditoria.registrarComoAtual("VAGA_ATUALIZADA", "vaga", vagaId, "sugestoes_descartadas=" + descartadas);
        if (!grupos.isEmpty()) {
            auditoria.registrarComoAtual("VAGA_AFIRMATIVA", "vaga", vagaId,
                    "grupos=" + grupos.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        }
        return completa(vagas.saveAndFlush(vaga));
    }

    /** Exclui a vaga, desde que não haja candidatos enviados, em entrevista ou contratados (nesse caso, encerre-a). */
    @Transactional
    public void excluir(UUID vagaId) {
        if (!vagas.existsById(vagaId)) throw new RecursoNaoEncontradoException("Vaga", vagaId);
        if (matches.existsByVagaIdAndStatusIn(vagaId,
                List.of(StatusMatch.ENVIADO, StatusMatch.ENTREVISTA, StatusMatch.CONTRATADO))) {
            throw new RegraNegocioException(
                    "Há candidatos em andamento ou contratados nesta vaga. Encerre a vaga em vez de excluí-la.");
        }
        matches.apagarDaVaga(vagaId, List.of(StatusMatch.SUGERIDO, StatusMatch.RECUSADO));
        auditoria.registrarComoAtual("VAGA_EXCLUIDA", "vaga", vagaId, null);
        vagas.deleteById(vagaId);
    }

    /** Remove vazios e duplicados (sem acento/caixa), mantendo o texto original da primeira ocorrência. */
    static Set<String> normalizarSkills(List<String> bruto) {
        if (bruto == null) return new LinkedHashSet<>();
        Map<String, String> porChave = new LinkedHashMap<>();
        for (String s : bruto) {
            if (s == null || s.isBlank()) continue;
            String limpa = Skills.canonica(s);          // nome padrão do catálogo, se houver
            if (limpa == null) continue;
            porChave.putIfAbsent(TextoUtil.chave(limpa), limpa);
        }
        if (porChave.size() > MAX_SKILLS) {
            throw new RegraNegocioException("No máximo " + MAX_SKILLS + " habilidades por vaga.");
        }
        return new LinkedHashSet<>(porChave.values());
    }

    @Transactional(readOnly = true)
    public Vaga buscar(UUID vagaId) {
        return completa(vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId)));
    }

    @Transactional(readOnly = true)
    public List<Vaga> listarPorEmpresa(UUID empresaId) {
        if (!empresas.existsById(empresaId)) {
            throw new RecursoNaoEncontradoException("Empresa", empresaId);
        }
        return vagas.findByEmpresaId(empresaId).stream().map(VagaService::completa).toList();
    }

    @Transactional
    public Vaga alterarStatus(UUID vagaId, StatusVaga status) {
        Vaga vaga = vagas.findById(vagaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
        vaga.setStatus(status);
        return completa(vaga);
    }

    /** Inicializa as coleções preguiçosas enquanto a transação está aberta (o controller mapeia depois). */
    private static Vaga completa(Vaga v) {
        v.getSkills().size();
        v.getGruposPrioritarios().size();
        return v;
    }
}
