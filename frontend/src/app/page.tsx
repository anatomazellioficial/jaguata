"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { Logo } from "@/components/Logo";
import { Reveal } from "@/components/ui";

const line = {
  hidden: { y: "110%" },
  show: (i: number) => ({ y: 0, transition: { duration: 0.9, delay: 0.1 + i * 0.12, ease: [0.22, 1, 0.36, 1] as const } }),
};

const PASSOS = [
  ["01", "A empresa escolhe o cargo", "Sem texto livre: um cardápio fechado, com a régua certa para cada porte."],
  ["02", "O motor cruza território e perfil", "CNAE, raio em km, modelo de trabalho e tipo de vínculo viram um score."],
  ["03", "A ONG chancela e envia", "Candidatos 18+ chegam com referência técnica e comportamental da instituição."],
];

const FAIXA = ["Trabalho decente", "ODS 8", "18+", "Chancela da ONG", "PCD", "Egressos", "50+", "Jovens da periferia"];

export default function Home() {
  return (
    <main className="overflow-hidden">
      {/* Hero sobre azul-marinho */}
      <div className="bg-navy text-cream">
        <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
          <Logo tone="light" />
          <nav className="flex items-center gap-3 text-sm font-semibold uppercase tracking-wide">
            <Link href="/login" className="px-3 py-2 text-cream/70 transition-colors hover:text-cream">Entrar</Link>
            <Link href="/registro" className="rounded-full bg-orange px-5 py-2.5 font-bold text-navy transition-colors hover:bg-sun">
              Começar
            </Link>
          </nav>
        </header>

        <section className="mx-auto max-w-6xl px-6 pb-24 pt-14 sm:pt-24">
          <h1 className="display text-[11.5vw] sm:text-[8vw] lg:text-[6.5rem]">
            {["Talento que", "chega", "chancelado."].map((t, i) => (
              <span key={t} className="block overflow-hidden pb-[0.08em]">
                <motion.span custom={i} variants={line} initial="hidden" animate="show"
                  className={`block ${i === 2 ? "text-sun" : ""}`}>
                  {t}
                </motion.span>
              </span>
            ))}
          </h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.9, duration: 0.8 }}
            className="mt-10 max-w-xl text-lg text-cream/75">
            A ONG vira o RH que a sua PME não pode pagar: pré-qualifica e envia pessoas em vulnerabilidade
            social, todas maiores de 18 anos, direto para as suas vagas.
          </motion.p>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 1.1 }} className="mt-10 flex flex-wrap gap-3">
            <Link href="/registro?tipo=empresa" className="rounded-full bg-orange px-7 py-3.5 font-bold uppercase tracking-wide text-navy transition-colors hover:bg-sun">
              Sou empresa
            </Link>
            <Link href="/registro?tipo=ong" className="rounded-full border border-cream/30 px-7 py-3.5 font-bold uppercase tracking-wide text-cream transition-colors hover:border-cream">
              Sou ONG
            </Link>
          </motion.div>
        </section>
      </div>

      <div className="bg-sun py-5 text-navy" aria-hidden>
        <div className="marquee flex w-max gap-12 whitespace-nowrap text-2xl font-extrabold uppercase tracking-tight">
          {[...FAIXA, ...FAIXA].map((t, i) => (
            <span key={i} className="flex items-center gap-12">
              {t} <span className="text-brick">✦</span>
            </span>
          ))}
        </div>
      </div>

      <section className="mx-auto max-w-6xl px-6 py-28">
        <Reveal><h2 className="display mb-16 text-4xl sm:text-6xl">Como funciona</h2></Reveal>
        <div className="grid gap-px overflow-hidden rounded-3xl border border-line bg-line md:grid-cols-3">
          {PASSOS.map(([n, t, d], i) => (
            <Reveal key={n} delay={i * 0.1} className="bg-panel p-8">
              <span className="text-sm font-bold text-brick">{n}</span>
              <h3 className="mt-6 text-2xl font-bold leading-tight">{t}</h3>
              <p className="mt-3 text-mute">{d}</p>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 pb-32">
        <Reveal>
          <div className="rounded-3xl bg-orange p-10 text-navy sm:p-16">
            <h2 className="display text-4xl sm:text-6xl">Vaga aberta?<br />Tem gente pronta.</h2>
            <Link href="/registro" className="mt-10 inline-block rounded-full bg-navy px-7 py-3.5 font-bold uppercase tracking-wide text-cream transition-transform hover:scale-105">
              Criar conta
            </Link>
          </div>
        </Reveal>
      </section>

      <footer className="border-t border-line py-8 text-center text-sm text-mute">
        © {new Date().getFullYear()} Jaguatá · Empregabilidade em rede, ODS 8.
      </footer>
    </main>
  );
}
