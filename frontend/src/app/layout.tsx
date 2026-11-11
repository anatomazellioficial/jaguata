import type { Metadata } from "next";
import "@fontsource-variable/lexend";
import "./globals.css";

export const metadata: Metadata = {
  title: "Jaguatá — talento chancelado, vaga preenchida",
  description:
    "Plataforma que conecta PMEs a ONGs: a ONG chancela e envia talentos 18+ para as vagas da sua empresa.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
