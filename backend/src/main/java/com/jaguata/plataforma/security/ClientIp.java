package com.jaguata.plataforma.security;

import jakarta.servlet.http.HttpServletRequest;

/** Resolve o IP do cliente sem confiar cegamente em X-Forwarded-For. */
public final class ClientIp {
    private ClientIp() {
    }

    /**
     * Com {@code trustedHops = n}, usa a n-ésima entrada da direita de X-Forwarded-For: é a que o
     * proxy confiável mais próximo da aplicação anexou. Entradas à esquerda podem ser forjadas pelo cliente.
     * Sem o cabeçalho (ou com hops = 0), usa o endereço da conexão.
     */
    public static String resolve(HttpServletRequest req, int trustedHops) {
        if (trustedHops > 0) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                int idx = parts.length - trustedHops;
                if (idx >= 0) {
                    String ip = parts[idx].trim();
                    if (!ip.isEmpty() && ip.length() <= 45) return ip;
                }
            }
        }
        return req.getRemoteAddr();
    }
}
