package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.enums.*;
import com.jaguata.plataforma.service.MatchScoreCalculator;
import com.jaguata.plataforma.service.MatchScoreCalculator.Entrada;
import com.jaguata.plataforma.service.Motivo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchScoreCalculatorTest {

    private final MatchScoreCalculator calc = new MatchScoreCalculator();

    private static Entrada base(double dist, ModeloTrabalho vaga, ModeloTrabalho pref) {
        return new Entrada(dist, 10000, vaga, pref, SetorAtuacao.TECNOLOGIA_E_COMUNICACAO,
                Set.of(SetorAtuacao.TECNOLOGIA_E_COMUNICACAO), null, null, Set.of(), Set.of(), null, Set.of(), false, false);
    }

    private static double pontos(MatchScoreCalculator.Resultado r, String criterio) {
        return r.motivos().stream().filter(m -> m.criterio().equals(criterio)).mapToDouble(Motivo::pontos).sum();
    }

    @Test
    void pesosSomam100() {
        assertEquals(100, MatchScoreCalculator.TERRITORIO + MatchScoreCalculator.MODELO + MatchScoreCalculator.SETOR
                + MatchScoreCalculator.FORMACAO + MatchScoreCalculator.SKILLS + MatchScoreCalculator.REGIME
                + MatchScoreCalculator.PRIORIDADE);
    }

    @Test
    void remotoSemExigenciasValeCem() {
        var r = calc.calcular(base(99999, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO));
        assertEquals(new BigDecimal("100.00"), r.total());
        assertEquals(7, r.motivos().size());
    }

    @Test
    void territorioDecaiComDistancia() {
        var perto = calc.calcular(base(1000, ModeloTrabalho.PRESENCIAL, ModeloTrabalho.PRESENCIAL));
        var longe = calc.calcular(base(9000, ModeloTrabalho.PRESENCIAL, ModeloTrabalho.PRESENCIAL));
        assertTrue(perto.total().compareTo(longe.total()) > 0);
        assertEquals(27.0, pontos(perto, "TERRITORIO"));
    }

    @Test
    void setorForaDaAreaDaOngZera_eSemDeclaracaoVaiaMeio() {
        Entrada fora = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, SetorAtuacao.INDUSTRIA,
                Set.of(SetorAtuacao.EDUCACAO), null, null, Set.of(), Set.of(), null, Set.of(), false, false);
        assertEquals(0, pontos(calc.calcular(fora), "SETOR"));
        Entrada sem = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, SetorAtuacao.INDUSTRIA,
                Set.of(), null, null, Set.of(), Set.of(), null, Set.of(), false, false);
        assertEquals(7.5, pontos(calc.calcular(sem), "SETOR"));
    }

    @Test
    void formacao() {
        Entrada atende = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(),
                Escolaridade.MEDIO, Escolaridade.SUPERIOR, Set.of(), Set.of(), null, Set.of(), false, false);
        assertEquals(15, pontos(calc.calcular(atende), "FORMACAO"));
        Entrada abaixo = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(),
                Escolaridade.SUPERIOR, Escolaridade.MEDIO, Set.of(), Set.of(), null, Set.of(), false, false);
        assertEquals(0, pontos(calc.calcular(abaixo), "FORMACAO"));
    }

    @Test
    void skillsProporcional() {
        Entrada e = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(), null, null,
                Set.of("excel", "atendimento"), Set.of("excel"), null, Set.of(), false, false);
        assertEquals(7.5, pontos(calc.calcular(e), "SKILLS"));
    }

    @Test
    void regimeDiferenteZera() {
        Entrada e = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(), null, null,
                Set.of(), Set.of(), RegimeContratacao.CLT, Set.of(RegimeContratacao.PJ), false, false);
        assertEquals(0, pontos(calc.calcular(e), "REGIME"));
    }

    @Test
    void afirmativaPontuaSoOsDoGrupo_semExporDetalhe() {
        Entrada dentro = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(), null, null,
                Set.of(), Set.of(), null, Set.of(), true, true);
        Entrada fora = new Entrada(0, 1000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, null, Set.of(), null, null,
                Set.of(), Set.of(), null, Set.of(), true, false);
        assertEquals(10, pontos(calc.calcular(dentro), "PRIORIDADE"));
        assertEquals(0, pontos(calc.calcular(fora), "PRIORIDADE"));
        calc.calcular(fora).motivos().forEach(m -> assertFalse(
                String.valueOf(m.nota()).toLowerCase().matches(".*(mulher|negr|ind[ií]gena|pcd|egresso|trans).*")));
    }

    @Test
    void setorPorCnae() {
        assertEquals(SetorAtuacao.TECNOLOGIA_E_COMUNICACAO, SetorAtuacao.de("62.01-5-01"));
        assertEquals(SetorAtuacao.EDUCACAO, SetorAtuacao.de("8511200"));
        assertNull(SetorAtuacao.de(null));
    }
}
