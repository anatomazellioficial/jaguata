import type { Metadata, Viewport } from "next";
import "@fontsource-variable/lexend";
import "./globals.css";
import { headers } from "next/headers";
import { Providers } from "@/components/Providers";
import { SITE_DESCRICAO, SITE_NOME, SITE_TITULO, siteUrl } from "@/lib/site";

export function generateMetadata(): Metadata {
  return {
    metadataBase: new URL(siteUrl()),
    title: { default: SITE_TITULO, template: `%s · ${SITE_NOME}` },
    description: SITE_DESCRICAO,
    applicationName: SITE_NOME,
    keywords: [
      "vagas para PMEs", "ONG", "terceiro setor", "inclusão produtiva", "jovem aprendiz", "estágio", "ODS 8",
      "trabalho decente", "recrutamento social", "talentos em vulnerabilidade social",
    ],
    authors: [{ name: SITE_NOME }],
    creator: SITE_NOME,
    alternates: { canonical: "/" },
    openGraph: {
      type: "website", locale: "pt_BR", siteName: SITE_NOME, title: SITE_TITULO, description: SITE_DESCRICAO, url: "/",
    },
    twitter: { card: "summary_large_image", title: SITE_TITULO, description: SITE_DESCRICAO },
    manifest: "/manifest.webmanifest",
    formatDetection: { telephone: false, email: false, address: false },
    robots: { index: true, follow: true },
  };
}

export const viewport: Viewport = {
  themeColor: "#102e50",
  colorScheme: "light",
  width: "device-width",
  initialScale: 1,
};

/** Dados estruturados (schema.org): ajudam buscadores a entender quem é o site. Escapa "<" contra injeção. */
function jsonLd(base: string) {
  const dados = {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "Organization",
        "@id": `${base}/#organizacao`,
        name: SITE_NOME,
        url: base,
        logo: `${base}/icon-512.png`,
        description: SITE_DESCRICAO,
      },
      {
        "@type": "WebSite",
        "@id": `${base}/#site`,
        url: base,
        name: SITE_NOME,
        inLanguage: "pt-BR",
        publisher: { "@id": `${base}/#organizacao` },
      },
    ],
  };
  return JSON.stringify(dados).replace(/</g, "\\u003c");
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // ler o cabeçalho torna a renderização dinâmica: cada resposta recebe o seu nonce de CSP (ver src/proxy.ts)
  await headers();
  return (
    <html lang="pt-BR">
      <body>
        {/* application/ld+json não executa código, então a CSP com nonce não o bloqueia */}
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd(siteUrl()) }} />
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
