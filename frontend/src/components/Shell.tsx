"use client";

import { motion, useMotionValueEvent, useScroll, useTransform } from "framer-motion";
import { useState, type ReactNode } from "react";
import { logout } from "@/lib/api";
import { Logo } from "./Logo";
import { Button, easeOut, spring } from "./ui";

/**
 * Moldura das áreas logadas, no estilo iOS: barra de vidro que aparece ao rolar,
 * "large title" que vira título pequeno na barra, entrada suave do conteúdo.
 */
export function Shell({ title, subtitle, action, children }: {
  title: string; subtitle?: string; action?: ReactNode; children: ReactNode;
}) {
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
        <Logo />
        <motion.span style={{ opacity: miniOpacity, y: miniY }}
          className="pointer-events-none absolute left-1/2 hidden max-w-[40%] -translate-x-1/2 truncate text-sm font-semibold sm:block">
          {title}
        </motion.span>
        <div className="flex items-center gap-2">
          {action}
          <Button variant="ghost" size="sm" onClick={() => logout()}>Sair</Button>
        </div>
      </motion.header>

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
      <motion.p initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }}
        className="mt-6 text-center text-sm text-mute">
        {footer}
      </motion.p>
    </main>
  );
}
