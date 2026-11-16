package com.jaguata.plataforma.domain.enums;

import com.jaguata.plataforma.domain.Candidato;
import com.jaguata.plataforma.domain.CandidatoTag;
import com.jaguata.plataforma.service.TextoUtil;

import java.util.function.Predicate;

/**
 * Grupos que uma VAGA AFIRMATIVA pode priorizar (ação afirmativa declarada e justificada pela empresa).
 * A prioridade só AUMENTA a pontuação de quem pertence ao grupo: ninguém é excluído, e a empresa não vê a qual
 * grupo o candidato pertence (ver {@code MatchResponse}).
 */
public enum GrupoPrioritario {
    PCD("Pessoas com deficiência", c -> temTag(c, "pcd", "pessoa com deficiencia")),
    EGRESSO("Egressos do sistema prisional", c -> temTag(c, "egresso", "egressa")),
    MAIS_50("Pessoas com 50 anos ou mais", c -> c.getIdade() >= 50 || temTag(c, "50+")),
    MULHERES("Mulheres", c -> c.getGenero() == Genero.MULHER || c.getGenero() == Genero.MULHER_TRANS),
    PESSOAS_NEGRAS("Pessoas negras (pretas e pardas)", c -> c.getRacaCor() == RacaCor.PRETA || c.getRacaCor() == RacaCor.PARDA),
    INDIGENAS("Pessoas indígenas", c -> c.getRacaCor() == RacaCor.INDIGENA),
    PESSOAS_TRANS_E_NAO_BINARIAS("Pessoas trans e não binárias",
            c -> c.getGenero() == Genero.MULHER_TRANS || c.getGenero() == Genero.HOMEM_TRANS || c.getGenero() == Genero.NAO_BINARIO);

    private final String rotulo;
    private final Predicate<Candidato> pertence;

    GrupoPrioritario(String rotulo, Predicate<Candidato> pertence) {
        this.rotulo = rotulo;
        this.pertence = pertence;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean contem(Candidato c) {
        return pertence.test(c);
    }

    private static boolean temTag(Candidato c, String... chaves) {
        for (CandidatoTag t : c.getTags()) {
            String k = TextoUtil.chave(t.getTag());
            for (String chave : chaves) if (k.equals(chave)) return true;
        }
        return false;
    }
}
