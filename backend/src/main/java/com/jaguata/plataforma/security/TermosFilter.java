package com.jaguata.plataforma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bloqueia (403, {@code codigo: TERMOS_DESATUALIZADOS}) empresas e ONGs que ainda não aceitaram a versão vigente
 * dos Termos. Ficam liberadas /api/conta/** (para ver os dados e aceitar) e /api/auth/**. Administradores não
 * passam por este filtro (contas criadas por configuração, sem cadastro).
 */
public class TermosFilter extends OncePerRequestFilter {

    private final TermosAceite termos;

    public TermosFilter(TermosAceite termos) {
        this.termos = termos;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getRequestURI();
        return !p.startsWith("/api/") || p.startsWith("/api/auth/") || p.startsWith("/api/conta");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Jwt j && !"ADMIN".equals(j.getClaimAsString("papel"))
                && !termos.vigente(UUID.fromString(j.getSubject()))) {
            res.setStatus(403);
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Os Termos de Uso foram atualizados. Aceite a nova versão para continuar.\","
                    + "\"codigo\":\"TERMOS_DESATUALIZADOS\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
