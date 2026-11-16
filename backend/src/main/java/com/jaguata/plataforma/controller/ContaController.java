package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.dto.Requests.AlterarSenhaRequest;
import com.jaguata.plataforma.dto.Requests.ExcluirContaRequest;
import com.jaguata.plataforma.dto.Requests.NotificacoesRequest;
import com.jaguata.plataforma.dto.Responses.ContaDadosResponse;
import com.jaguata.plataforma.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Conta do usuário logado: troca de senha, sessões e direitos do titular (acesso e exclusão). */
@RestController
@RequestMapping("/api/conta")
public class ContaController {

    private final ContaService contas;

    public ContaController(ContaService contas) {
        this.contas = contas;
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/dados")
    public ContaDadosResponse dados(@AuthenticationPrincipal Jwt jwt) {
        return contas.dados(id(jwt));
    }

    /** Encerra todas as sessões (o usuário precisa entrar de novo). */
    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest req, @AuthenticationPrincipal Jwt jwt) {
        contas.alterarSenha(id(jwt), req.senhaAtual(), req.novaSenha());
    }

    public record TermosEstado(String versaoVigente, boolean pendente) {
    }

    @GetMapping("/termos")
    public TermosEstado termos(@AuthenticationPrincipal Jwt jwt) {
        return new TermosEstado(com.jaguata.plataforma.conta.Termos.VERSAO, contas.dados(id(jwt)).termosPendentes());
    }

    @PostMapping("/termos/aceitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void aceitarTermos(@AuthenticationPrincipal Jwt jwt) {
        contas.aceitarTermos(id(jwt));
    }

    @PutMapping("/notificacoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notificacoes(@Valid @RequestBody NotificacoesRequest req, @AuthenticationPrincipal Jwt jwt) {
        contas.notificacoes(id(jwt), req.ativas(), req.sugestoes(), req.andamento(), req.resumoDiario());
    }

    @PostMapping("/sessoes/encerrar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sairDeTodos(@AuthenticationPrincipal Jwt jwt) {
        contas.sairDeTodosOsDispositivos(id(jwt));
    }

    /** Exclui o acesso (exige a senha). Não vale para administradores. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@Valid @RequestBody ExcluirContaRequest req, @AuthenticationPrincipal Jwt jwt) {
        contas.excluirConta(id(jwt), req.senha());
    }
}
