import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Protótipo de demonstração",
  description: "Passeio guiado pelo fluxo da plataforma, com dados fictícios e sem backend.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
