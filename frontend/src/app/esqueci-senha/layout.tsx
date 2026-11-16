import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Esqueci minha senha",
  description: "Receba um link para redefinir a senha da sua conta.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
