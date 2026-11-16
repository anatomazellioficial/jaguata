package com.jaguata.plataforma.domain.enums;

/**
 * Setores econômicos agrupando as divisões da CNAE (2 primeiros dígitos). A ONG declara em quais setores atua
 * e a vaga herda o setor da empresa pelo CNAE: é esse cruzamento que mede a afinidade no match.
 */
public enum SetorAtuacao {
    AGRONEGOCIO("Agronegócio", 1, 3),
    INDUSTRIA("Indústria", 5, 33),
    UTILIDADES_E_MEIO_AMBIENTE("Utilidades e meio ambiente", 35, 39),
    CONSTRUCAO("Construção", 41, 43),
    COMERCIO("Comércio", 45, 47),
    LOGISTICA_E_TRANSPORTE("Logística e transporte", 49, 53),
    ALIMENTACAO_E_HOSPEDAGEM("Alimentação e hospedagem", 55, 56),
    TECNOLOGIA_E_COMUNICACAO("Tecnologia e comunicação", 58, 63),
    FINANCEIRO_E_SEGUROS("Financeiro e seguros", 64, 66),
    SERVICOS_PROFISSIONAIS("Serviços profissionais", 68, 75),
    SERVICOS_ADMINISTRATIVOS("Serviços administrativos", 77, 82),
    EDUCACAO("Educação", 85, 85),
    SAUDE_E_ASSISTENCIA("Saúde e assistência social", 86, 88),
    ARTES_CULTURA_E_ESPORTE("Artes, cultura e esporte", 90, 93),
    OUTROS_SERVICOS("Outros serviços", 94, 99);

    private final String rotulo;
    private final int de;
    private final int ate;

    SetorAtuacao(String rotulo, int de, int ate) {
        this.rotulo = rotulo;
        this.de = de;
        this.ate = ate;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Setor de um CNAE (qualquer formato: "6201-5/01", "6201501"). Null se não houver correspondência. */
    public static SetorAtuacao de(String cnae) {
        if (cnae == null) return null;
        String digitos = cnae.replaceAll("\\D", "");
        if (digitos.length() < 2) return null;
        int divisao = Integer.parseInt(digitos.substring(0, 2));
        for (SetorAtuacao s : values()) {
            if (divisao >= s.de && divisao <= s.ate) return s;
        }
        return null;   // administração pública (84) e demais divisões fora do escopo
    }
}
