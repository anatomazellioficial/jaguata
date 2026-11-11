package com.jaguata.plataforma.controller;

import com.jaguata.plataforma.domain.Usuario;
import com.jaguata.plataforma.dto.Requests.LoginRequest;
import com.jaguata.plataforma.dto.Requests.RegistroEmpresaRequest;
import com.jaguata.plataforma.dto.Requests.RegistroOngRequest;
import com.jaguata.plataforma.dto.Responses.RegistroResponse;
import com.jaguata.plataforma.dto.Responses.TokenResponse;
import com.jaguata.plataforma.security.JwtService;
import com.jaguata.plataforma.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @PostMapping("/registro/empresa")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrarEmpresa(@Valid @RequestBody RegistroEmpresaRequest req) {
        Usuario u = auth.registrarEmpresa(req.email(), req.senha(), req.empresa());
        return new RegistroResponse(u.getEmpresaId(), u.getEmail(), u.getPapel());
    }

    @PostMapping("/registro/ong")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroResponse registrarOng(@Valid @RequestBody RegistroOngRequest req) {
        Usuario u = auth.registrarOng(req.email(), req.senha(), req.ong());
        return new RegistroResponse(u.getOngId(), u.getEmail(), u.getPapel());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        Usuario u = auth.autenticar(req.email(), req.senha());
        return new TokenResponse(jwt.emitir(u), "Bearer", jwt.getTtl().toSeconds());
    }
}
