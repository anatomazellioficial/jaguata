package com.jaguata.plataforma.dto;

import com.jaguata.plataforma.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
            @NotBlank @Size(max = 18) String cnpj,
            @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade.") boolean aceiteTermos) {
    }

    public record RegistroOngRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String senha,
            @NotBlank @Size(max = 18) String cnpj,
            @NotNull NivelCapacidade nivelCapacidade,
            @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade.") boolean aceiteTermos) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
    }

    public record TokenRequest(@NotBlank @Size(max = 128) String token) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record RedefinirSenhaRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String novaSenha) {
    }

    public record AlterarSenhaRequest(
            @NotBlank @Size(max = 72) String senhaAtual,
            @NotBlank @Size(min = 10, max = 72, message = "Senha deve ter de 10 a 72 caracteres") String novaSenha) {
    }

    public record ExcluirContaRequest(@NotBlank @Size(max = 72) String senha) {
    }

    public record TagRequest(@NotNull CategoriaTag categoria, @NotBlank @Size(max = 80) String tag) {
    }

    public record CandidatoRequest(
            @NotBlank String nomeCompleto,
            @NotNull @Past LocalDate dataNascimento,
            @NotBlank @Size(max = 9) String cep,
            @NotNull ModeloTrabalho modeloTrabalhoPref,
            @Valid List<TagRequest> tags,
            Escolaridade escolaridade,
            /** Autodeclaração opcional; usada só em vagas afirmativas e nunca exibida às empresas. */
            Genero genero,
            RacaCor racaCor,
            @Size(max = 3) Set<RegimeContratacao> regimes,
            /** A ONG atesta que o(a) assistido(a) assinou o termo de consentimento para o tratamento dos dados. */
            @AssertTrue(message = "É necessário confirmar o consentimento do(a) assistido(a).") boolean consentimentoConfirmado) {
    }

    public record VagaRequest(
            @NotNull NivelCargo nivelCargo,
            @NotNull TipoContrato tipoContrato,
            @NotNull ModeloTrabalho modeloTrabalho,
            @NotBlank @Size(max = 9) String cepAtuacao,
            Escolaridade escolaridadeMinima,
            RegimeContratacao regime,
            @Size(max = 15, message = "No máximo 15 habilidades") List<@NotBlank @Size(max = 80) String> skills,
            /** Vaga afirmativa: só aumenta a pontuação de quem pertence aos grupos; exige justificativa. */
            @Size(max = 7) Set<GrupoPrioritario> gruposPrioritarios,
            @Size(max = 500) String justificativaAfirmativa) {
    }

    public record SetoresRequest(@NotNull @Size(max = 15) Set<SetorAtuacao> setores) {
    }

    public record StatusVagaRequest(@NotNull StatusVaga status) {
    }

    public record StatusMatchRequest(@NotNull StatusMatch status) {
    }
}
