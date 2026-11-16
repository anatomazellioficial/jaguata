import type { Metadata } from "next";
import "@fontsource-variable/lexend";
import "./globals.css";
import { headers } from "next/headers";
import { Providers } from "@/components/Providers";

export const metadata: Metadata = {
  title: "Jaguatá — talento chancelado, vaga preenchida",
  description:
    "Plataforma que conecta PMEs a ONGs: a ONG chancela e envia talentos 18+ para as vagas da sua empresa.",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // ler o cabecalho torna a renderizacao dinamica: cada resposta recebe o seu nonce de CSP (ver src/proxy.ts)
  await headers();
  return (
    <html lang="pt-BR">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
