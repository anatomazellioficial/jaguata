package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Responses.ConsultaCepResponse;
import com.jaguata.plataforma.dto.Responses.ConsultaCnpjResponse;
import com.jaguata.plataforma.integracao.ConsultaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pré-visualização de CNPJ e CEP nos formulários. As rotas são públicas (o cadastro ainda não tem conta),
 * por isso têm rate limit próprio e só devolvem dados públicos. O quadro societário nunca é exposto.
 */
@RestController
@RequestMapping("/api/consulta")
public class ConsultaController {

    private final ConsultaService consulta;

    public ConsultaController(ConsultaService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/cnpj/{cnpj}")
    public ConsultaCnpjResponse cnpj(@PathVariable String cnpj) {
        return ConsultaCnpjResponse.from(consulta.cnpj(cnpj));
    }

    @GetMapping("/cep/{cep}")
    public ConsultaCepResponse cep(@PathVariable String cep) {
        return ConsultaCepResponse.from(consulta.cep(cep));
    }
}
