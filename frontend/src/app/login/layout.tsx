import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Entrar",
  description: "Acesse a sua conta na Jaguatá: empresas, ONGs e administradores.",
  alternates: { canonical: "/login" },
  openGraph: { title: "Entrar", description: "Acesse a sua conta na Jaguatá: empresas, ONGs e administradores.", url: "/login" },
  robots: { index: true, follow: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
