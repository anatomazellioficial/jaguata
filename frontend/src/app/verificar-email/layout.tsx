import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Confirmar e-mail",
  description: "Confirmação de e-mail da sua conta.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
