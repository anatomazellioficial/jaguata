import type { NextConfig } from "next";

const isProd = process.env.NODE_ENV === "production";

// A Content-Security-Policy NAO fica aqui: ela leva um nonce por requisicao e e montada em src/proxy.ts.

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
    ];
    if (isProd) base.push({ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" });
    return [{ source: "/(.*)", headers: base }];
  },
};

export default config;
