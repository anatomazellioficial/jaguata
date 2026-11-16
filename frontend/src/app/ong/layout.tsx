import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Painel da ONG",
  description: "Candidatos, áreas de atuação e funil de vagas.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
