import Link from "next/link";
import type { ReactNode } from "react";
import { Logo } from "./Logo";

/** Moldura das páginas legais (Termos e Privacidade). Texto estático, renderizado no servidor. */
export function LegalPage({ titulo, versao, children }: { titulo: string; versao: string; children: ReactNode }) {
  return (
    <main className="mx-auto max-w-3xl px-5 pb-24 pt-8">
      <header className="flex items-center justify-between">
        <Logo />
        <nav className="flex gap-4 text-sm font-medium text-mute">
          <Link href="/termos" className="hover:text-navy">Termos de Uso</Link>
          <Link href="/privacidade" className="hover:text-navy">Privacidade</Link>
        </nav>
      </header>

      <h1 className="title mb-2 mt-14 text-4xl sm:text-5xl">{titulo}</h1>
      <p className="mb-6 text-sm text-mute">Versão {versao}</p>

      <div role="note" className="mb-10 rounded-2xl bg-sun/30 px-5 py-4 text-sm">
        <strong>Versão preliminar.</strong> Este texto é um modelo técnico que descreve como a plataforma funciona hoje.
        Os trechos entre [colchetes] precisam ser definidos e todo o conteúdo deve passar por revisão jurídica
        antes de ser usado com usuários reais.
      </div>

      <article className="space-y-6 text-[15px] leading-relaxed [&_h2]:title [&_h2]:mt-10 [&_h2]:text-2xl [&_li]:ml-5 [&_li]:list-disc [&_li]:py-0.5 [&_ul]:mt-2">
        {children}
      </article>
    </main>
  );
}
