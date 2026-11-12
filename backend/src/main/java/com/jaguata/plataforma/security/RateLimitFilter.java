package com.jaguata.plataforma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Rate limit das rotas da API. Roda DEPOIS da validação do Bearer, então já conhece o usuário:
 * <ul>
 *   <li>POST /api/auth/login: por IP (anti força bruta);</li>
 *   <li>POST /api/auth/registro/**: por IP (anti criação em massa);</li>
 *   <li>demais rotas: por usuário autenticado (não dá para driblar trocando de IP) ou, sem token, por IP.</li>
 * </ul>
 * Instanciado manualmente na cadeia de segurança (não é @Component) para não ser registrado duas vezes.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final SecurityProperties.Limites cfg;

    public RateLimitFilter(RateLimiter limiter, SecurityProperties.Limites cfg) {
        this.limiter = limiter;
        this.cfg = cfg;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !cfg.enabled() || !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean post = HttpMethod.POST.matches(req.getMethod());
        String ip = ClientIp.resolve(req, cfg.trustedProxyHops());

        RateLimiter.Result r;
        if (post && path.equals("/api/auth/login")) {
            r = limiter.tryConsume("login:" + ip, cfg.loginPerMinute(), Duration.ofMinutes(1));
        } else if (post && path.startsWith("/api/auth/registro")) {
            r = limiter.tryConsume("registro:" + ip, cfg.registroPerHour(), Duration.ofHours(1));
        } else {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            String key = a != null && a.getPrincipal() instanceof Jwt j ? "user:" + j.getSubject() : "ip:" + ip;
            r = limiter.tryConsume("api:" + key, cfg.apiPerMinute(), Duration.ofMinutes(1));
        }

        if (!r.allowed()) {
            res.setStatus(429);
            res.setHeader("Retry-After", Long.toString(r.retryAfterSeconds()));
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Muitas requisições. Tente novamente em " + r.retryAfterSeconds() + " s.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
