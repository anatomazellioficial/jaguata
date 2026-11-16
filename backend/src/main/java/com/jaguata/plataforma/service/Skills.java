package com.jaguata.plataforma.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de habilidades sugeridas. O texto livre continua permitido (nem toda habilidade está na lista), mas
 * quando coincide com uma do catálogo (sem acento/caixa) é gravado com o nome padrão. Assim "excel", "EXCEL" e
 * "Excél" viram a mesma coisa para o casamento entre vaga e candidato.
 */
public final class Skills {
    private Skills() {
    }

    public static final List<String> CATALOGO = List.of(
            "Atendimento ao cliente", "Atendimento por telefone", "Vendas", "Negociação", "Telemarketing", "Recepção",
            "Caixa / PDV", "Cobrança", "Comunicação", "Trabalho em equipe", "Liderança", "Proatividade", "Organização",
            "Pontualidade", "Informática básica", "Pacote Office", "Excel", "Word", "Digitação",
            "Rotinas administrativas", "Arquivo e documentação", "Departamento pessoal", "Financeiro", "Faturamento",
            "Contabilidade", "Compras", "Estoque", "Almoxarifado", "Logística", "Entregas", "Motorista",
            "Operador de empilhadeira", "CNH A", "CNH B", "CNH C", "CNH D", "Serviços gerais", "Limpeza",
            "Manutenção predial", "Elétrica", "Hidráulica", "Pintura", "Alvenaria", "Soldagem", "Costura",
            "Cozinha", "Auxiliar de cozinha", "Confeitaria", "Atendimento em salão", "Cuidador", "Enfermagem",
            "Segurança do trabalho", "Redes sociais", "Marketing digital", "Design gráfico", "Edição de vídeo",
            "Fotografia", "Suporte técnico", "Lógica de programação", "HTML e CSS", "JavaScript", "Python", "SQL",
            "Inglês básico", "Inglês intermediário", "Espanhol", "Libras");

    private static final Map<String, String> POR_CHAVE = new LinkedHashMap<>();

    static {
        for (String s : CATALOGO) POR_CHAVE.put(TextoUtil.chave(s), s);
    }

    /** Nome padrão se a habilidade está no catálogo; senão, o texto limpo e limitado a 80 caracteres. */
    public static String canonica(String bruto) {
        if (bruto == null) return null;
        String limpa = bruto.trim();
        if (limpa.isEmpty()) return null;
        String padrao = POR_CHAVE.get(TextoUtil.chave(limpa));
        if (padrao != null) return padrao;
        return limpa.length() > 80 ? limpa.substring(0, 80) : limpa;
    }
}
