import type { NextConfig } from "next";

const isProd = process.env.NODE_ENV === "production";

// CSP restritiva. 'unsafe-inline' é necessário porque o Next injeta scripts/estilos inline de hidratação;
// o próximo passo de endurecimento é CSP com nonce por requisição. Em dev o React precisa de eval.
const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isProd ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    const base = [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      // geolocalização só nesta origem (botão "usar minha localização"); o resto fica bloqueado
      { key: "Permissions-Policy", value: "geolocation=(self), camera=(), microphone=(), payment=()" },
      { key: "Cross-Origin-Opener-Policy", value: "same-origin" },
      { key: "Content-Security-Policy", value: csp },
    ];
    if (isProd) base.push({ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" });
    return [{ source: "/(.*)", headers: base }];
  },
};

export default config;
