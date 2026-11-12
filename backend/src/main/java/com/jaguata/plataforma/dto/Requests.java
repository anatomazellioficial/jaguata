package com.jaguata.plataforma.dto;

import com.jaguata.plataforma.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Payloads de entrada da API. Dados que vêm de fontes oficiais (razão social, CNAE, porte, endereço e
 * coordenadas) NÃO são aceitos do cliente: o servidor os obtém da Receita Federal e do CEP.
 */
public final class Requests {
    private Requests() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
    }

    /** O CNPJ pode vir formatado ou só com dígitos; o servidor valida e normaliza. */
    public record RegistroEmpresaRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String senha,
            @NotBlank @Size(max = 18) String cnpj) {
    }

    public record RegistroOngRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String senha,
            @NotBlank @Size(max = 18) String cnpj,
            @NotNull NivelCapacidade nivelCapacidade) {
    }

    public record TagRequest(@NotNull CategoriaTag categoria, @NotBlank @Size(max = 80) String tag) {
    }

    public record CandidatoRequest(
            @NotBlank String nomeCompleto,
            @NotNull @Past LocalDate dataNascimento,
            @NotBlank @Size(max = 9) String cep,
            @NotNull ModeloTrabalho modeloTrabalhoPref,
            @Valid List<TagRequest> tags) {
    }

    public record VagaRequest(
            @NotNull NivelCargo nivelCargo,
            @NotNull TipoContrato tipoContrato,
            @NotNull ModeloTrabalho modeloTrabalho,
            @NotBlank @Size(max = 9) String cepAtuacao) {
    }

    public record StatusVagaRequest(@NotNull StatusVaga status) {
    }

    public record StatusMatchRequest(@NotNull StatusMatch status) {
    }
}
