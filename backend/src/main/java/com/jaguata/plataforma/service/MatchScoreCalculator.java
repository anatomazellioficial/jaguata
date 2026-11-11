package com.jaguata.plataforma.service;

import com.jaguata.plataforma.domain.enums.ModeloTrabalho;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Score 0-100:
 * <ul>
 *   <li>Território (até 50): decai linearmente com a distância até o raio; vaga remota = 50.</li>
 *   <li>Modelo de trabalho (até 20): igual = 20; um dos lados HÍBRIDO = 10.</li>
 *   <li>CNAE (30): empresa x foco da ONG compatíveis.</li>
 * </ul>
 */
@Component
public class MatchScoreCalculator {

    static final double PESO_TERRITORIO = 50;
    static final double PESO_MODELO = 20;
    static final double PESO_CNAE = 30;

    public BigDecimal calcular(double distanciaMetros, double raioMetros,
                               ModeloTrabalho modeloVaga, ModeloTrabalho preferenciaCandidato,
                               boolean cnaeCompativel) {
        double territorio;
        if (modeloVaga == ModeloTrabalho.REMOTO) {
            territorio = PESO_TERRITORIO;
        } else if (raioMetros <= 0) {
            territorio = 0;
        } else {
            territorio = PESO_TERRITORIO * Math.max(0, 1 - distanciaMetros / raioMetros);
        }

        double modelo = 0;
        if (modeloVaga == preferenciaCandidato) {
            modelo = PESO_MODELO;
        } else if (modeloVaga == ModeloTrabalho.HIBRIDO || preferenciaCandidato == ModeloTrabalho.HIBRIDO) {
            modelo = PESO_MODELO / 2;
        }

        double cnae = cnaeCompativel ? PESO_CNAE : 0;

        return BigDecimal.valueOf(territorio + modelo + cnae).setScale(2, RoundingMode.HALF_UP);
    }
}
