import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Termos atualizados",
  description: "Aceite a versão atual dos Termos de Uso e da Política de Privacidade.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
