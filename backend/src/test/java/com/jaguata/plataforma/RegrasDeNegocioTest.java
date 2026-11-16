package com.jaguata.plataforma;

import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import com.jaguata.plataforma.domain.enums.NivelCargo;
import com.jaguata.plataforma.domain.enums.PorteEmpresa;
import com.jaguata.plataforma.domain.enums.StatusMatch;
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
}
