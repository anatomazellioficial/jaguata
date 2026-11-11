package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.PorteEmpresa;
import com.jaguata.plataforma.domain.enums.StatusMatch;
import com.jaguata.plataforma.service.CnaeCompatibilidade;
import com.jaguata.plataforma.service.MatchScoreCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RegrasDeNegocioTest {

    @Test
    void matrizDeComplexidade_rebaixaDiretorParaMicroEPequena() {
        assertEquals("Coordenador/Gerente", NivelCargo.DIRETOR.tituloExibicaoPara(PorteEmpresa.ME));
        assertEquals("Coordenador/Gerente", NivelCargo.CEO.tituloExibicaoPara(PorteEmpresa.EPP));
        assertEquals("Diretor", NivelCargo.DIRETOR.tituloExibicaoPara(PorteEmpresa.DEMAIS));
        assertEquals("Analista", NivelCargo.ANALISTA.tituloExibicaoPara(PorteEmpresa.MEI));
    }

    @Test
    void idade_dezoitoAnosCompletosSoNoAniversario() {
        LocalDate hoje = LocalDate.of(2026, 11, 11);
        assertEquals(18, Candidato.calcularIdade(LocalDate.of(2008, 11, 11), hoje));
        assertEquals(17, Candidato.calcularIdade(LocalDate.of(2008, 11, 12), hoje));
    }

    @Test
    void funil_transicoesValidas() {
        assertTrue(StatusMatch.SUGERIDO.podeTransitarPara(StatusMatch.ENVIADO));
        assertTrue(StatusMatch.ENTREVISTA.podeTransitarPara(StatusMatch.CONTRATADO));
        assertFalse(StatusMatch.SUGERIDO.podeTransitarPara(StatusMatch.CONTRATADO));
        assertFalse(StatusMatch.CONTRATADO.podeTransitarPara(StatusMatch.RECUSADO));
    }

    @Test
    void score_maximoParaVagaRemotaIgualECnaeCompativel() {
        var calc = new MatchScoreCalculator();
        assertEquals(new BigDecimal("100.00"),
                calc.calcular(99999, 10000, ModeloTrabalho.REMOTO, ModeloTrabalho.REMOTO, true));
    }

    @Test
    void score_decaiComDistancia() {
        var calc = new MatchScoreCalculator();
        BigDecimal perto = calc.calcular(1000, 10000, ModeloTrabalho.PRESENCIAL, ModeloTrabalho.PRESENCIAL, false);
        BigDecimal longe = calc.calcular(9000, 10000, ModeloTrabalho.PRESENCIAL, ModeloTrabalho.PRESENCIAL, false);
        assertTrue(perto.compareTo(longe) > 0);
        assertEquals(new BigDecimal("65.00"), perto);
    }

    @Test
    void cnae_mesmaDivisao() {
        var p = new CnaeCompatibilidade();
        assertTrue(p.compativel("62.01-5-01", "6209100"));
        assertFalse(p.compativel("62.01-5-01", "94.30-8-00"));
        assertFalse(p.compativel(null, "6201501"));
    }
}
