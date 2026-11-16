import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Minha conta",
  description: "Dados de acesso, segurança e privacidade.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
