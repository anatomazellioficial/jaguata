import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Painel da empresa",
  description: "Vagas e candidatos chancelados pelas ONGs.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
