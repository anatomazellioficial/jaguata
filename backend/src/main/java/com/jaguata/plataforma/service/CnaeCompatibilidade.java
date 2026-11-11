package com.jaguata.plataforma.service;

import org.springframework.stereotype.Component;

/**
 * Política de compatibilidade de CNAE entre empresa e foco da ONG.
 * Implementação inicial: mesma divisão CNAE (2 primeiros dígitos).
 * Isolada em um componente para ser trocada por uma tabela de afinidade depois.
 */
@Component
public class CnaeCompatibilidade {

    public boolean compativel(String cnaeEmpresa, String cnaeOng) {
        String a = divisao(cnaeEmpresa);
        String b = divisao(cnaeOng);
        return !a.isEmpty() && a.equals(b);
    }

    private String divisao(String cnae) {
        if (cnae == null) return "";
        String digitos = cnae.replaceAll("\\D", "");
        return digitos.length() >= 2 ? digitos.substring(0, 2) : "";
    }
}
