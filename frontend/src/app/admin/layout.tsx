import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Administração",
  description: "Verificação de ONGs, indicadores e auditoria.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
