package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.dto.Requests.EmailRequest;
import com.jaguata.plataforma.dto.Requests.LoginRequest;
import com.jaguata.plataforma.dto.Requests.RedefinirSenhaRequest;
import com.jaguata.plataforma.dto.Requests.RefreshRequest;
import com.jaguata.plataforma.dto.Requests.RegistroEmpresaRequest;
import com.jaguata.plataforma.dto.Requests.RegistroOngRequest;
import com.jaguata.plataforma.dto.Requests.TokenRequest;
import com.jaguata.plataforma.dto.Responses.RegistroResponse;
import com.jaguata.plataforma.dto.Responses.TokenResponse;
import com.jaguata.plataforma.service.AuthService;
import com.jaguata.plataforma.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Rotas públicas de acesso: cadastro, login, renovação, logout, verificação de e-mail e senha esquecida. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final ContaService contas;

    public AuthController(AuthService auth, ContaService contas) {
        this.auth = auth;
        this.contas = contas;
    }

    @PostMapping("/registro/empresa")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrarEmpresa(@Valid @RequestBody RegistroEmpresaRequest req) {
        Usuario u = auth.registrarEmpresa(req.email(), req.senha(), req.cnpj());
        return new RegistroResponse(u.getEmpresaId(), u.getEmail(), u.getPapel(), true);
    }

    @PostMapping("/registro/ong")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrarOng(@Valid @RequestBody RegistroOngRequest req) {
        Usuario u = auth.registrarOng(req.email(), req.senha(), req.cnpj(), req.nivelCapacidade());
        return new RegistroResponse(u.getOngId(), u.getEmail(), u.getPapel(), true);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        Usuario u = auth.autenticar(req.email(), req.senha());
        return TokenResponse.de(auth.iniciarSessao(u, userAgent));
    }

    /** Troca o refresh token por um par novo. O token antigo deixa de valer; reuso derruba a sessão inteira. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req,
                                 @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return TokenResponse.de(auth.renovar(req.refreshToken(), userAgent));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.sair(req.refreshToken());
    }

    @PostMapping("/email/verificar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verificarEmail(@Valid @RequestBody TokenRequest req) {
        contas.verificarEmail(req.token());
    }

    /** Sempre 202, exista ou não o e-mail (não revela quais contas existem). */
    @PostMapping("/email/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarVerificacao(@Valid @RequestBody EmailRequest req) {
        contas.reenviarVerificacao(req.email());
    }

    /** Sempre 202, exista ou não o e-mail. */
    @PostMapping("/senha/esqueci")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void esqueciSenha(@Valid @RequestBody EmailRequest req) {
        contas.esqueciSenha(req.email());
    }

    @PostMapping("/senha/redefinir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
        contas.redefinirSenha(req.token(), req.novaSenha());
    }
}
