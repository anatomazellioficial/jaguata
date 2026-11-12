package com.jaguata.plataforma;

import com.jaguata.plataforma.integracao.Documentos;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gera CNPJs com dígitos verificadores válidos. Os 2 primeiros dígitos escolhem o comportamento do
 * {@link IntegracoesStub} (ex.: "11" = ME, "22" = EPP, "33" = MEI, "44" = DEMAIS, "99" = inativo).
 */
public final class TestCnpjs {
    private TestCnpjs() {
    }

    public static String com(String prefixo) {
        long meio = ThreadLocalRandom.current().nextLong(0, 1_000_000L);
        return Documentos.completarCnpj(prefixo + String.format("%06d", meio) + "0001");
    }
}
