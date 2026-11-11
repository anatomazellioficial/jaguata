package com.jaguata.plataforma.domain.enums;

/** Cardápio fechado de cargos. A empresa nunca digita o cargo livremente. */
public enum NivelCargo {
    APRENDIZ("Jovem Aprendiz"),
    ESTAGIARIO("Estagiário"),
    TRAINEE("Trainee"),
    ASSISTENTE("Assistente"),
    ANALISTA("Analista"),
    COORDENADOR("Coordenador / Supervisor"),
    GERENTE("Gerente"),
    DIRETOR("Diretor"),
    CEO("CEO");

    public static final int APRENDIZ_IDADE_MIN = 18;
    public static final int APRENDIZ_IDADE_MAX = 24;

    private final String rotulo;

    NivelCargo(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * Matriz de Complexidade: para micro/pequenas empresas (MEI, ME, EPP),
     * cargos de alto escalão (Diretor, CEO) são exibidos à ONG rebaixados
     * para "Coordenador/Gerente". Médio/grande porte segue a régua estrita.
     */
    public String tituloExibicaoPara(PorteEmpresa porte) {
        if (porte.isMicroOuPequena() && (this == DIRETOR || this == CEO)) {
            return "Coordenador/Gerente";
        }
        return rotulo;
    }
}
