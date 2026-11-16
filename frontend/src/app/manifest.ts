import type { MetadataRoute } from "next";
import { SITE_DESCRICAO, SITE_NOME } from "@/lib/site";

/** Manifesto web: permite "adicionar à tela inicial" com o ícone do J. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: `${SITE_NOME} — talento chancelado, vaga preenchida`,
    short_name: SITE_NOME,
    description: SITE_DESCRICAO,
    lang: "pt-BR",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#fbf5ee",
    theme_color: "#102e50",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
