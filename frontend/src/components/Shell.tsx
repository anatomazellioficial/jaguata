"use client";

import { motion, useMotionValueEvent, useScroll, useTransform } from "framer-motion";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { getSession } from "@/lib/api";
import { homeFor } from "@/lib/jwt";
import { Logo } from "./Logo";
import { Button, easeOut, spring } from "./ui";
import { logout } from "@/lib/api";

/**
 * Moldura das áreas logadas, no estilo iOS: barra de vidro que aparece ao rolar,
 * "large title" que vira título pequeno na barra, entrada suave do conteúdo.
 */
const Icone = {
  painel: <path d="M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10" />,
  conta: <><circle cx="12" cy="8.5" r="3.5" /><path d="M5 20c.6-3.6 3.4-5.5 7-5.5s6.4 1.9 7 5.5" /></>,
  mais: <path d="M12 5v14M5 12h14" />,
};
function Svg({ children }: { children: ReactNode }) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.9"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden>{children}</svg>
  );
}

export function Shell({ title, subtitle, action, mobileAction, children }: {
  title: string; subtitle?: string; action?: ReactNode; children: ReactNode;
  /** Ação principal da página no menu do celular (só ícone). */
  mobileAction?: { label: string; onClick: () => void };
}) {
  const pathname = usePathname();
  const [home, setHome] = useState("/");
  useEffect(() => { getSession().then((s) => setHome(homeFor(s.papel))).catch(() => undefined); }, []);
  const aba = (ativa: boolean) =>
    `flex h-11 w-14 items-center justify-center rounded-full transition-colors ${ativa ? "bg-navy/[.08] text-navy" : "text-mute active:bg-navy/[.06]"}`;
  const { scrollY } = useScroll();
  const [scrolled, setScrolled] = useState(false);
  useMotionValueEvent(scrollY, "change", (v) => setScrolled(v > 12));
  const miniOpacity = useTransform(scrollY, [50, 110], [0, 1]);
  const miniY = useTransform(scrollY, [50, 110], [8, 0]);

  return (
    <div className="mx-auto max-w-6xl px-4 pb-28 sm:px-6">
      <motion.header
        initial={{ y: -24, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={spring}
        className={`sticky top-3 z-40 mt-3 flex items-center justify-between rounded-full px-4 py-2.5 transition-[background,box-shadow] duration-300 ${scrolled ? "glass" : "border border-transparent"}`}
      >
        <span className="whitespace-nowrap"><Logo /></span>
        <motion.span style={{ opacity: miniOpacity, y: miniY }}
          className="pointer-events-none absolute left-1/2 hidden max-w-[40%] -translate-x-1/2 truncate text-sm font-semibold sm:block">
          {title}
        </motion.span>
        <div className="hidden items-center gap-2 sm:flex">
          {action}
          <Link href="/conta" className="rounded-full px-3 py-1.5 text-[13px] font-semibold text-mute transition-colors hover:bg-navy/[.06] hover:text-navy">Conta</Link>
          <Button variant="ghost" size="sm" onClick={() => logout()}>Sair</Button>
        </div>
      </motion.header>

      {/* Mobile: barra de abas de vidro fixa na base, como no iPhone */}
      <motion.nav aria-label="Menu principal"
        initial={{ y: 80, opacity: 0 }} animate={{ y: 0, opacity: 1 }} transition={spring}
        style={{ marginBottom: "env(safe-area-inset-bottom)" }}
        className="glass fixed inset-x-4 bottom-3 z-40 flex items-center justify-around gap-1 rounded-full px-3 py-2 sm:hidden">
        <Link href={home} aria-label="Painel" className={aba(pathname === home)}><Svg>{Icone.painel}</Svg></Link>
        {mobileAction && (
          <button type="button" aria-label={mobileAction.label} onClick={mobileAction.onClick}
            className="flex h-12 w-12 items-center justify-center rounded-full bg-orange text-white shadow-md active:scale-95">
            <Svg>{Icone.mais}</Svg>
          </button>
        )}
        <Link href="/conta" aria-label="Minha conta" className={aba(pathname === "/conta")}><Svg>{Icone.conta}</Svg></Link>
      </motion.nav>

      <motion.div
        initial={{ opacity: 0, y: 18 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7, ease: easeOut }}
        className="mb-10 mt-12 sm:mt-16"
      >
        <h1 className="title text-[2.6rem] sm:text-6xl">{title}</h1>
        {subtitle && <p className="mt-4 max-w-xl text-[17px] leading-relaxed text-mute">{subtitle}</p>}
      </motion.div>

      <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.8, delay: 0.1, ease: easeOut }}>
        {children}
      </motion.div>
    </div>
  );
}

/** Moldura de login/registro: cartão de vidro centralizado que entra com mola. */
export function AuthFrame({ title, subtitle, children, footer }: {
  title: string; subtitle?: string; children: ReactNode; footer: ReactNode;
}) {
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-5 py-12">
      <motion.div initial={{ opacity: 0, y: 24, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: "spring", stiffness: 200, damping: 24 }}
        className="glass-strong rounded-[36px] p-7 sm:p-9">
        <Logo />
        <h1 className="title mb-2 mt-9 text-4xl">{title}</h1>
        {subtitle && <p className="mb-7 text-[15px] text-mute">{subtitle}</p>}
        {!subtitle && <div className="mb-7" />}
        {children}
      </motion.div>
      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }}
        className="mt-6 space-y-3 text-center text-sm text-mute">
        <p>{footer}</p>
        <p className="text-xs">
          <Link href="/termos" className="hover:text-navy hover:underline">Termos de Uso</Link>
          {" · "}
          <Link href="/privacidade" className="hover:text-navy hover:underline">Privacidade</Link>
        </p>
      </motion.div>
    </main>
  );
}
