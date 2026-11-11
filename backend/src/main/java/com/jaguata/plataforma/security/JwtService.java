package com.jaguata.plataforma.security;

import com.jaguata.plataforma.domain.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${app.jwt.ttl:PT1H}") Duration ttl) {
        this.encoder = encoder;
        this.ttl = ttl;
    }

    public Duration getTtl() {
        return ttl;
    }

    public String emitir(Usuario u) {
        Instant agora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(u.getId().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(ttl))
                .claim("papel", u.getPapel().name());
        if (u.getEmpresaId() != null) claims.claim("empresaId", u.getEmpresaId().toString());
        if (u.getOngId() != null) claims.claim("ongId", u.getOngId().toString());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
