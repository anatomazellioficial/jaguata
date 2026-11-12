"use client";

import Link from "next/link";
import { motion, useScroll, useTransform } from "framer-motion";
import { useRef } from "react";
import { Logo } from "@/components/Logo";
import { Avatar, Reveal, ScoreRing, easeOut, spring } from "@/components/ui";

const CARGOS = ["Jovem Aprendiz", "Estagiário", "Trainee", "Assistente", "Analista", "Coordenador", "Gerente", "Diretor", "CEO"];

/* ------------------------------------------------------------------ navegação flutuante */
function Nav() {
  return (
    <motion.nav
      initial={{ y: -30, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={{ ...spring, delay: 0.1 }}
      className="glass fixed left-1/2 top-4 z-50 flex w-[min(94vw,64rem)] -translate-x-1/2 items-center justify-between rounded-full px-4 py-2.5"
    >
      <Logo />
      <div className="hidden items-center gap-7 text-sm font-medium text-mute md:flex">
        <a href="#como" className="transition-colors hover:text-navy">Como funciona</a>
        <a href="#recursos" className="transition-colors hover:text-navy">Recursos</a>
        <a href="#para-quem" className="transition-colors hover:text-navy">Para quem</a>
      </div>
      <div className="flex items-center gap-1.5">
        <Link href="/login" className="rounded-full px-4 py-2 text-sm font-semibold text-navy transition-colors hover:bg-navy/5">Entrar</Link>
        <Link href="/registro" className="rounded-full bg-navy px-4 py-2 text-sm font-semibold text-cream transition-transform hover:scale-105 active:scale-95">
          Começar
        </Link>
      </div>
    </motion.nav>
  );
}

/* ------------------------------------------------------------------ cartões flutuantes do hero */
const float = (delay = 0, amp = 8) => ({
  animate: { y: [0, -amp, 0] },
  transition: { duration: 6, delay, repeat: Infinity, ease: "easeInOut" as const },
});

function HeroCards() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const yA = useTransform(scrollYProgress, [0, 1], [50, -70]);
  const yB = useTransform(scrollYProgress, [0, 1], [90, -30]);
  const yC = useTransform(scrollYProgress, [0, 1], [20, -110]);

  return (
    <div ref={ref} className="relative mx-auto mt-16 grid max-w-5xl gap-5 px-2 md:mt-24 md:block md:h-[430px]">
      {/* vaga */}
      <motion.div style={{ y: yB }} className="md:absolute md:left-0 md:top-24 md:w-[300px]">
        <motion.div initial={{ opacity: 0, y: 40, rotate: -6 }} animate={{ opacity: 1, y: 0, rotate: -3 }}
          transition={{ type: "spring", stiffness: 110, damping: 16, delay: 0.55 }}>
          <motion.div {...float(0.4, 7)} className="glass-strong rounded-[28px] p-5">
            <p className="text-xs font-medium text-mute">Vaga publicada pela empresa</p>
            <p className="mt-2 text-[15px] text-mute line-through decoration-brick/60">Diretor</p>
            <motion.p initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 1.3 }}
              className="title mt-0.5 text-2xl">Coordenador/Gerente</motion.p>
            <div className="mt-4 flex flex-wrap gap-2 text-xs font-semibold">
              <span className="rounded-full bg-sun/50 px-3 py-1">Porte ME</span>
              <span className="rounded-full bg-navy/[.06] px-3 py-1 text-mute">Visto pela ONG</span>
            </div>
          </motion.div>
        </motion.div>
      </motion.div>

      {/* match (central) */}
      <motion.div style={{ y: yA }} className="md:absolute md:left-1/2 md:top-0 md:w-[360px] md:-translate-x-1/2">
        <motion.div initial={{ opacity: 0, y: 50, scale: 0.94 }} animate={{ opacity: 1, y: 0, scale: 1 }}
          transition={{ type: "spring", stiffness: 110, damping: 16, delay: 0.4 }}>
          <motion.div {...float(0, 10)} className="glass-strong rounded-[32px] p-6">
            <div className="flex items-center gap-3.5">
              <Avatar name="Maria Souza" size={52} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-lg font-semibold tracking-tight">Maria Souza</p>
                <p className="text-sm text-mute">Analista · a 4 km</p>
              </div>
              <ScoreRing score={92} size={58} />
            </div>
            <div className="mt-5 flex flex-wrap gap-2 text-xs font-semibold">
              <span className="rounded-full bg-navy/[.06] px-3 py-1">PCD</span>
              <span className="rounded-full bg-navy/[.06] px-3 py-1">Egressa</span>
              <span className="rounded-full bg-navy/[.06] px-3 py-1">Híbrido</span>
            </div>
            <div className="mt-5 flex items-center gap-2.5 rounded-2xl bg-sun/30 px-3.5 py-2.5 text-sm font-medium">
              <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", delay: 1.2, stiffness: 300, damping: 14 }}
                className="flex h-5 w-5 items-center justify-center rounded-full bg-navy text-[10px] text-cream">✓</motion.span>
              Chancelada pela ONG Futuro
            </div>
          </motion.div>
        </motion.div>
        <p className="mt-3 text-center text-xs text-mute">Exemplo ilustrativo</p>
      </motion.div>

      {/* funil */}
      <motion.div style={{ y: yC }} className="md:absolute md:right-0 md:top-36 md:w-[290px]">
        <motion.div initial={{ opacity: 0, y: 40, rotate: 6 }} animate={{ opacity: 1, y: 0, rotate: 3 }}
          transition={{ type: "spring", stiffness: 110, damping: 16, delay: 0.7 }}>
          <motion.div {...float(0.9, 7)} className="glass-strong rounded-[28px] p-5">
            <p className="text-xs font-medium text-mute">Funil de contratação</p>
            <div className="relative mt-5 flex items-center justify-between">
              <span className="absolute left-2 right-2 top-1/2 h-0.5 -translate-y-1/2 rounded-full bg-navy/10" />
              <motion.span initial={{ width: 0 }} animate={{ width: "62%" }} transition={{ delay: 1.2, duration: 1.2, ease: easeOut }}
                className="absolute left-2 top-1/2 h-0.5 -translate-y-1/2 rounded-full bg-orange" />
              {[0, 1, 2, 3].map((i) => (
                <motion.span key={i} initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ delay: 0.9 + i * 0.12, type: "spring", stiffness: 300, damping: 18 }}
                  className={`relative h-4 w-4 rounded-full ring-4 ring-white/80 ${i <= 2 ? "bg-orange" : "bg-navy/15"}`} />
              ))}
            </div>
            <div className="mt-3 flex justify-between text-[10px] font-medium text-mute">
              <span>Sugerido</span><span>Enviado</span><span>Entrevista</span><span>Contratado</span>
            </div>
          </motion.div>
        </motion.div>
      </motion.div>
    </div>
  );
}

/* ------------------------------------------------------------------ bento */
function Bento() {
  const tile = "glass relative overflow-hidden rounded-[32px] p-7";
  return (
    <div id="recursos" className="grid grid-cols-1 gap-4 md:grid-cols-6">
      <Reveal className="md:col-span-4">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full`}>
          <p className="text-sm font-semibold text-orange">Cardápio fechado</p>
          <h3 className="title mt-2 text-3xl">Nove cargos. Zero texto livre.</h3>
          <p className="mt-2 max-w-md text-mute">Cada nível já nasce com a régua certa, sem ambiguidade para a ONG.</p>
          <div className="mt-6 flex flex-wrap gap-2">
            {CARGOS.map((c, i) => (
              <motion.span key={c} initial={{ opacity: 0, scale: 0.7 }} whileInView={{ opacity: 1, scale: 1 }} viewport={{ once: true }}
                transition={{ type: "spring", stiffness: 300, damping: 18, delay: i * 0.05 }}
                whileHover={{ scale: 1.08, y: -2 }}
                className={`rounded-full px-4 py-2 text-sm font-semibold ${i === 4 ? "bg-orange text-navy" : "bg-navy/[.06]"}`}>
                {c}
              </motion.span>
            ))}
          </div>
        </motion.div>
      </Reveal>

      <Reveal delay={0.08} className="md:col-span-2">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full bg-gradient-to-br from-sun/60 to-orange/40`}>
          <p className="text-sm font-semibold">Somente</p>
          <p className="display mt-1 text-8xl">18<span className="text-navy/60">+</span></p>
          <p className="mt-3 text-sm font-medium">Todos os candidatos são maiores de idade, garantido no banco de dados.</p>
        </motion.div>
      </Reveal>

      <Reveal delay={0.04} className="md:col-span-2">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full`}>
          <p className="text-sm font-semibold text-orange">Território</p>
          <h3 className="title mt-2 text-2xl">Match por raio em km</h3>
          <div className="relative mx-auto mt-6 flex h-36 w-36 items-center justify-center">
            {[0, 1, 2].map((i) => (
              <span key={i} className="ping-slow absolute inset-0 rounded-full border-2 border-orange/50" style={{ animationDelay: `${i * 1.05}s` }} />
            ))}
            <span className="absolute inset-6 rounded-full border border-navy/10" />
            <span className="absolute inset-12 rounded-full border border-navy/10" />
            <span className="relative h-4 w-4 rounded-full bg-orange shadow-[0_0_0_6px_rgba(231,139,72,.25)]" />
            <motion.span animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 9, ease: "linear" }} className="absolute inset-0">
              <span className="absolute left-1/2 top-3 h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-navy" />
            </motion.span>
          </div>
        </motion.div>
      </Reveal>

      <Reveal delay={0.08} className="md:col-span-4">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full`}>
          <p className="text-sm font-semibold text-orange">Matriz de complexidade</p>
          <h3 className="title mt-2 text-3xl">O cargo certo para o porte certo.</h3>
          <p className="mt-2 max-w-md text-mute">Micro e pequenas empresas podem chamar o cargo de “Diretor”; a ONG enxerga o nível real da vaga.</p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <span className="rounded-2xl bg-navy/[.06] px-4 py-2.5 font-semibold">Diretor</span>
            <motion.span animate={{ x: [0, 6, 0] }} transition={{ repeat: Infinity, duration: 1.8, ease: "easeInOut" }} className="text-2xl text-orange">→</motion.span>
            <span className="rounded-2xl bg-orange px-4 py-2.5 font-semibold text-navy shadow-[0_8px_20px_-8px_rgba(231,139,72,.8)]">Coordenador/Gerente</span>
          </div>
        </motion.div>
      </Reveal>

      <Reveal delay={0.04} className="md:col-span-3">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full`}>
          <div className="flex items-center gap-4">
            <motion.span initial={{ scale: 0, rotate: -40 }} whileInView={{ scale: 1, rotate: 0 }} viewport={{ once: true }}
              transition={{ type: "spring", stiffness: 260, damping: 14 }}
              className="flex h-14 w-14 items-center justify-center rounded-full bg-navy text-2xl text-cream">✓</motion.span>
            <div>
              <h3 className="title text-2xl">Chancela da ONG</h3>
              <p className="text-sm text-mute">Só ONGs verificadas enviam candidatos.</p>
            </div>
          </div>
        </motion.div>
      </Reveal>

      <Reveal delay={0.08} className="md:col-span-3">
        <motion.div whileHover={{ y: -4 }} transition={spring} className={`${tile} h-full`}>
          <h3 className="title text-2xl">Funil transparente</h3>
          <p className="mt-1 text-sm text-mute">Do sugerido ao contratado, cada etapa com dono.</p>
          <div className="mt-5 flex items-center gap-2">
            {["Sugerido", "Enviado", "Entrevista", "Contratado"].map((s, i) => (
              <motion.span key={s} initial={{ opacity: 0, y: 10 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}
                transition={{ delay: i * 0.1, ...spring }}
                className={`rounded-full px-3 py-1 text-xs font-semibold ${i === 3 ? "bg-navy text-cream" : i === 2 ? "bg-sun/60" : "bg-navy/[.06]"}`}>
                {s}
              </motion.span>
            ))}
          </div>
        </motion.div>
      </Reveal>
    </div>
  );
}

/* ------------------------------------------------------------------ página */
export default function Home() {
  const hero = useRef<HTMLElement>(null);
  const { scrollYProgress } = useScroll({ target: hero, offset: ["start start", "end start"] });
  const titleY = useTransform(scrollYProgress, [0, 1], [0, 80]);
  const titleOpacity = useTransform(scrollYProgress, [0, 0.9], [1, 0.2]);

  return (
    <main className="overflow-x-clip">
      <Nav />

      <section ref={hero} className="mx-auto max-w-6xl px-5 pb-16 pt-36 text-center sm:pt-44">
        <motion.div style={{ y: titleY, opacity: titleOpacity }}>
          <motion.div initial={{ opacity: 0, y: 10, scale: 0.95 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ ...spring, delay: 0.1 }}
            className="glass mx-auto mb-8 inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-[13px] font-medium">
            <span className="h-2 w-2 rounded-full bg-orange" /> Empregabilidade em rede · ODS 8
          </motion.div>
          <h1 className="display text-[2.9rem] sm:text-7xl lg:text-[5.5rem]">
            {["Talento que chega", "chancelado."].map((t, i) => (
              <span key={t} className="block overflow-hidden pb-[0.12em]">
                <motion.span initial={{ y: "110%" }} animate={{ y: 0 }} transition={{ duration: 1, delay: 0.2 + i * 0.14, ease: easeOut }}
                  className={`block ${i === 1 ? "gradient-text" : ""}`}>
                  {t}
                </motion.span>
              </span>
            ))}
          </h1>
          <motion.p initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.7, duration: 0.9, ease: easeOut }}
            className="mx-auto mt-8 max-w-2xl text-lg leading-relaxed text-mute sm:text-xl">
            A ONG vira o RH que a sua PME não pode pagar: pré-qualifica e envia pessoas em vulnerabilidade social,
            todas maiores de 18 anos, direto para as suas vagas.
          </motion.p>
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.9, duration: 0.9, ease: easeOut }}
            className="mt-10 flex flex-wrap items-center justify-center gap-3">
            <Link href="/registro?tipo=empresa">
              <motion.span whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.96 }} transition={spring}
                className="inline-block rounded-full bg-orange px-7 py-3.5 font-semibold text-navy shadow-[0_10px_26px_-8px_rgba(231,139,72,.8)]">
                Sou empresa
              </motion.span>
            </Link>
            <Link href="/registro?tipo=ong">
              <motion.span whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.96 }} transition={spring}
                className="glass inline-block rounded-full px-7 py-3.5 font-semibold">
                Sou ONG
              </motion.span>
            </Link>
          </motion.div>
        </motion.div>

        <HeroCards />
      </section>

      <section id="como" className="mx-auto max-w-6xl px-5 py-24">
        <Reveal><h2 className="title mb-3 text-4xl sm:text-6xl">Como funciona</h2></Reveal>
        <Reveal delay={0.05}><p className="mb-12 max-w-xl text-lg text-mute">Três passos, sem planilha e sem intermediário.</p></Reveal>
        <div className="grid gap-4 md:grid-cols-3">
          {[
            ["1", "A empresa escolhe o cargo", "Pelo cardápio fechado, com a régua certa para o porte."],
            ["2", "O motor cruza território e perfil", "CNAE, raio em km, modelo de trabalho e vínculo viram um score."],
            ["3", "A ONG chancela e envia", "Candidatos 18+ chegam com a referência da instituição."],
          ].map(([n, t, d], i) => (
            <Reveal key={n} delay={i * 0.1}>
              <motion.div whileHover={{ y: -5 }} transition={spring} className="glass h-full rounded-[32px] p-7">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-orange text-lg font-bold text-navy">{n}</span>
                <h3 className="title mt-6 text-2xl">{t}</h3>
                <p className="mt-2 text-mute">{d}</p>
              </motion.div>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-5 py-12">
        <Reveal><h2 className="title mb-3 text-4xl sm:text-6xl">Pensada nos detalhes.</h2></Reveal>
        <Reveal delay={0.05}><p className="mb-12 max-w-xl text-lg text-mute">Regras do negócio garantidas no código e no banco.</p></Reveal>
        <Bento />
      </section>

      <section id="para-quem" className="mx-auto max-w-6xl px-5 py-24">
        <div className="grid gap-4 md:grid-cols-2">
          {[
            { t: "Para empresas", d: "Receba candidatos já pré-qualificados e chancelados, sem montar um RH.", l: ["Cardápio de vagas pronto", "Match por território", "Acompanhe o funil"], href: "/registro?tipo=empresa", dark: true },
            { t: "Para ONGs", d: "Conecte seus assistidos a decisores dispostos a contratar.", l: ["Banco de talentos com tags", "Fila de sugestões", "Envio com chancela"], href: "/registro?tipo=ong", dark: false },
          ].map((c, i) => (
            <Reveal key={c.t} delay={i * 0.1}>
              <motion.div whileHover={{ y: -5 }} transition={spring}
                className={`h-full rounded-[36px] p-9 ${c.dark ? "bg-navy text-cream shadow-lift" : "glass"}`}>
                <h3 className="title text-3xl">{c.t}</h3>
                <p className={`mt-2 ${c.dark ? "text-cream/70" : "text-mute"}`}>{c.d}</p>
                <ul className="mt-6 space-y-3">
                  {c.l.map((x) => (
                    <li key={x} className="flex items-center gap-3 font-medium">
                      <span className={`flex h-5 w-5 items-center justify-center rounded-full text-[10px] ${c.dark ? "bg-orange text-navy" : "bg-navy text-cream"}`}>✓</span>
                      {x}
                    </li>
                  ))}
                </ul>
                <Link href={c.href}>
                  <motion.span whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.96 }} transition={spring}
                    className={`mt-8 inline-block rounded-full px-6 py-3 font-semibold ${c.dark ? "bg-orange text-navy" : "bg-navy text-cream"}`}>
                    Começar agora
                  </motion.span>
                </Link>
              </motion.div>
            </Reveal>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-5 pb-28">
        <Reveal>
          <div className="relative overflow-hidden rounded-[44px] bg-navy px-8 py-20 text-center text-cream">
            <motion.span animate={{ x: [0, 40, 0], y: [0, -20, 0] }} transition={{ repeat: Infinity, duration: 14, ease: "easeInOut" }}
              className="absolute -left-20 -top-24 h-72 w-72 rounded-full bg-orange/40 blur-3xl" />
            <motion.span animate={{ x: [0, -40, 0], y: [0, 24, 0] }} transition={{ repeat: Infinity, duration: 16, ease: "easeInOut" }}
              className="absolute -bottom-24 -right-16 h-80 w-80 rounded-full bg-sun/30 blur-3xl" />
            <h2 className="title relative text-4xl sm:text-6xl">Vaga aberta?<br />Tem gente pronta.</h2>
            <Link href="/registro" className="relative">
              <motion.span whileHover={{ scale: 1.06 }} whileTap={{ scale: 0.96 }} transition={spring}
                className="mt-10 inline-block rounded-full bg-orange px-8 py-4 font-semibold text-navy">
                Criar conta
              </motion.span>
            </Link>
          </div>
        </Reveal>
      </section>

      <footer className="border-t border-navy/5 py-10 text-center text-sm text-mute">
        © {new Date().getFullYear()} Jaguatá · Empregabilidade em rede, ODS 8.
      </footer>
    </main>
  );
}
