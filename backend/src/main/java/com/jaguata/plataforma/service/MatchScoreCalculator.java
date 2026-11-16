package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.enums.Escolaridade;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.RegimeContratacao;
import com.jaguata.plataforma.domain.enums.SetorAtuacao;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Score de 0 a 100, somando 7 critérios (cada um explicado em um {@link Motivo}):
 * <pre>
 *  TERRITORIO 30  distância até o raio (vaga remota = máximo)
 *  MODELO     10  presencial/híbrido/remoto
 *  SETOR      15  setores da ONG x setor (CNAE) da empresa
 *  FORMACAO   15  escolaridade mínima da vaga
 *  SKILLS     15  habilidades desejadas encontradas
 *  REGIME      5  CLT/PJ/cooperado
 *  PRIORIDADE 10  vaga afirmativa: só quem pertence a um grupo priorizado recebe estes pontos
 * </pre>
 * Critério sem exigência da vaga (ou sem dado do candidato, quando neutro) vale o máximo: o score mede
 * adequação ao que a vaga pediu. Nenhum critério exclui ninguém; exclusões são regras de elegibilidade
 * (verificação da ONG, idade do aprendiz, estágio, vaga aberta) e filtros explícitos da empresa.
 */
@Component
public class MatchScoreCalculator {

    public static final int TERRITORIO = 30;
    public static final int MODELO = 10;
    public static final int SETOR = 15;
    public static final int FORMACAO = 15;
    public static final int SKILLS = 15;
    public static final int REGIME = 5;
    public static final int PRIORIDADE = 10;

    /** Dados de entrada já "achatados" (sem JPA), para o cálculo ser puro e testável. */
    public record Entrada(
            double distanciaMetros, double raioMetros,
            ModeloTrabalho modeloVaga, ModeloTrabalho preferenciaCandidato,
            SetorAtuacao setorEmpresa, Set<SetorAtuacao> setoresOng,
            Escolaridade escolaridadeMinima, Escolaridade escolaridadeCandidato,
            Set<String> skillsVaga, Set<String> skillsCandidato,
            RegimeContratacao regimeVaga, Set<RegimeContratacao> regimesCandidato,
            boolean vagaAfirmativa, boolean candidatoNoGrupoPrioritario) {
    }

    public record Resultado(BigDecimal total, List<Motivo> motivos) {
    }

    public Resultado calcular(Entrada e) {
        List<Motivo> m = new ArrayList<>();
        m.add(territorio(e));
        m.add(modelo(e));
        m.add(setor(e));
        m.add(formacao(e));
        m.add(skills(e));
        m.add(regime(e));
        m.add(prioridade(e));
        double soma = m.stream().mapToDouble(Motivo::pontos).sum();
        return new Resultado(BigDecimal.valueOf(soma).setScale(2, RoundingMode.HALF_UP), List.copyOf(m));
    }

    private Motivo territorio(Entrada e) {
        if (e.modeloVaga() == ModeloTrabalho.REMOTO) return new Motivo("TERRITORIO", TERRITORIO, TERRITORIO, "vaga remota");
        if (e.raioMetros() <= 0) return new Motivo("TERRITORIO", 0, TERRITORIO, null);
        double p = TERRITORIO * Math.max(0, 1 - e.distanciaMetros() / e.raioMetros());
        return new Motivo("TERRITORIO", arredondar(p), TERRITORIO,
                String.format(Locale.forLanguageTag("pt-BR"), "a %.1f km", e.distanciaMetros() / 1000));
    }

    private Motivo modelo(Entrada e) {
        if (e.modeloVaga() == e.preferenciaCandidato()) return new Motivo("MODELO", MODELO, MODELO, "preferência coincide");
        if (e.modeloVaga() == ModeloTrabalho.HIBRIDO || e.preferenciaCandidato() == ModeloTrabalho.HIBRIDO) {
            return new Motivo("MODELO", MODELO / 2.0, MODELO, "compatível (híbrido)");
        }
        return new Motivo("MODELO", 0, MODELO, "preferência diferente");
    }

    private Motivo setor(Entrada e) {
        if (e.setorEmpresa() == null) return new Motivo("SETOR", SETOR / 2.0, SETOR, "setor da empresa não classificado");
        if (e.setoresOng() == null || e.setoresOng().isEmpty()) {
            return new Motivo("SETOR", SETOR / 2.0, SETOR, "ONG ainda não declarou áreas de atuação");
        }
        return e.setoresOng().contains(e.setorEmpresa())
                ? new Motivo("SETOR", SETOR, SETOR, "ONG atua no setor da empresa")
                : new Motivo("SETOR", 0, SETOR, "fora das áreas de atuação da ONG");
    }

    private Motivo formacao(Entrada e) {
        Escolaridade min = e.escolaridadeMinima();
        if (min == null) return new Motivo("FORMACAO", FORMACAO, FORMACAO, "sem exigência");
        Escolaridade c = e.escolaridadeCandidato();
        if (c == null) return new Motivo("FORMACAO", FORMACAO / 3.0, FORMACAO, "formação não informada");
        if (c.atende(min)) return new Motivo("FORMACAO", FORMACAO, FORMACAO, "atende a exigência");
        if (min.ordinal() - c.ordinal() == 1) return new Motivo("FORMACAO", FORMACAO / 3.0, FORMACAO, "um nível abaixo do pedido");
        return new Motivo("FORMACAO", 0, FORMACAO, "abaixo do pedido");
    }

    private Motivo skills(Entrada e) {
        Set<String> pedidas = e.skillsVaga();
        if (pedidas == null || pedidas.isEmpty()) return new Motivo("SKILLS", SKILLS, SKILLS, "sem exigência");
        long achadas = pedidas.stream().filter(s -> e.skillsCandidato() != null && e.skillsCandidato().contains(s)).count();
        return new Motivo("SKILLS", arredondar(SKILLS * (double) achadas / pedidas.size()), SKILLS,
                achadas + " de " + pedidas.size() + " habilidades");
    }

    private Motivo regime(Entrada e) {
        if (e.regimeVaga() == null) return new Motivo("REGIME", REGIME, REGIME, "sem exigência");
        if (e.regimesCandidato() == null || e.regimesCandidato().isEmpty() || e.regimesCandidato().contains(e.regimeVaga())) {
            return new Motivo("REGIME", REGIME, REGIME, "compatível");
        }
        return new Motivo("REGIME", 0, REGIME, "regime diferente do preferido");
    }

    /** Sem nome de grupo e sem detalhe: só a pontuação, para não expor atributos sensíveis. */
    private Motivo prioridade(Entrada e) {
        if (!e.vagaAfirmativa()) return new Motivo("PRIORIDADE", PRIORIDADE, PRIORIDADE, "vaga sem prioridade");
        return new Motivo("PRIORIDADE", e.candidatoNoGrupoPrioritario() ? PRIORIDADE : 0, PRIORIDADE, "vaga afirmativa");
    }

    private static double arredondar(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
