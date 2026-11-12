package com.jaguata.plataforma.dto;

import com.jaguata.plataforma.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Payloads de entrada da API. */
public final class Requests {
    private Requests() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String senha) {
    }

    public record RegistroEmpresaRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String senha,
            @NotNull @Valid EmpresaRequest empresa) {
    }

    public record RegistroOngRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String senha,
            @NotNull @Valid OngRequest ong) {
    }

    public record EmpresaRequest(
            @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ deve ter 14 dígitos") String cnpj,
            @NotBlank String razaoSocial,
            @NotBlank String cnaePrincipal,
            @NotNull PorteEmpresa porteReceita,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP deve ter 8 dígitos") String cep,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record OngRequest(
            @NotBlank @Pattern(regexp = "\\d{14}", message = "CNPJ deve ter 14 dígitos") String cnpj,
            @NotBlank String razaoSocial,
            @NotBlank String cnaePrincipal,
            @NotNull NivelCapacidade nivelCapacidade,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP deve ter 8 dígitos") String cep,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record TagRequest(@NotNull CategoriaTag categoria, @NotBlank @Size(max = 80) String tag) {
    }

    public record CandidatoRequest(
            @NotBlank String nomeCompleto,
            @NotNull @Past LocalDate dataNascimento,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP deve ter 8 dígitos") String cep,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull ModeloTrabalho modeloTrabalhoPref,
            @Valid List<TagRequest> tags) {
    }

    public record VagaRequest(
            @NotNull NivelCargo nivelCargo,
            @NotNull TipoContrato tipoContrato,
            @NotNull ModeloTrabalho modeloTrabalho,
            @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP deve ter 8 dígitos") String cepAtuacao,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record StatusVagaRequest(@NotNull StatusVaga status) {
    }

    public record StatusMatchRequest(@NotNull StatusMatch status) {
    }
}
