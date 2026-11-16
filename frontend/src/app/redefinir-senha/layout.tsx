import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Redefinir senha",
  description: "Defina uma nova senha para a sua conta.",
  robots: { index: false, follow: false, nocache: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
