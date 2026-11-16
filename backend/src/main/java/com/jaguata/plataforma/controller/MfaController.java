package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.conta.TotpService;
import com.jaguata.plataforma.dto.Requests.CodigoTotpRequest;
import com.jaguata.plataforma.dto.Requests.DesativarTotpRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Segundo fator (TOTP) dos administradores. */
@RestController
@RequestMapping("/api/conta/2fa")
@PreAuthorize("hasRole('ADMIN')")
public class MfaController {

    public record Estado(boolean ativo) {
    }

    private final TotpService totp;

    public MfaController(TotpService totp) {
        this.totp = totp;
    }

    @GetMapping
    public Estado estado(@AuthenticationPrincipal Jwt jwt) {
        return new Estado(totp.ativo(id(jwt)));
    }

    /** Gera o segredo (e o link otpauth:// para o QR code). Só aparece nesta resposta. */
    @PostMapping("/configurar")
    public TotpService.Configuracao configurar(@AuthenticationPrincipal Jwt jwt) {
        return totp.iniciar(id(jwt));
    }

    @PostMapping("/ativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ativar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodigoTotpRequest req) {
        totp.ativar(id(jwt), req.codigo());
    }

    @PostMapping("/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DesativarTotpRequest req) {
        totp.desativar(id(jwt), req.senha(), req.codigo());
    }

    private static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
