import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Criar conta",
  description: "Cadastre sua empresa ou ONG na Jaguatá com o CNPJ: os dados vêm da Receita Federal e o match leva talentos chancelados às suas vagas.",
  alternates: { canonical: "/registro" },
  openGraph: { title: "Criar conta", description: "Cadastre sua empresa ou ONG na Jaguatá com o CNPJ: os dados vêm da Receita Federal e o match leva talentos chancelados às suas vagas.", url: "/registro" },
  robots: { index: true, follow: true },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
