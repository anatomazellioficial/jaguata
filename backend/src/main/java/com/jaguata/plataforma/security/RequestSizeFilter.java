package com.jaguata.plataforma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Limita o tamanho do corpo das requisições da API. Recusa cedo por Content-Length e, para
 * requisições "chunked" (sem Content-Length), interrompe a leitura ao passar do limite.
 */
public class RequestSizeFilter extends OncePerRequestFilter {

    private final int maxBytes;

    public RequestSizeFilter(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > maxBytes) {
            res.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Payload Too Large\",\"status\":413,"
                    + "\"detail\":\"Corpo da requisição grande demais.\"}");
            return;
        }
        chain.doFilter(new Limited(req, maxBytes), res);
    }

    private static final class Limited extends HttpServletRequestWrapper {
        private final int max;

        Limited(HttpServletRequest req, int max) {
            super(req);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long count;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++count > max) throw new IOException("Corpo da requisição excede o limite");
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0 && (count += n) > max) throw new IOException("Corpo da requisição excede o limite");
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener l) {
                    in.setReadListener(l);
                }
            };
        }
    }
}
