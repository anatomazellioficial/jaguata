"use client";

import { motion } from "framer-motion";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { ROTULO_STATUS, type StatusMatch } from "@/lib/types";

export function Reveal({ children, delay = 0, className }: { children: ReactNode; delay?: number; className?: string }) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 28 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-60px" }}
      transition={{ duration: 0.7, delay, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "ghost" | "danger"; loading?: boolean };

export function Button({ variant = "primary", loading, className = "", children, disabled, ...rest }: BtnProps) {
  const styles = {
    primary: "bg-orange text-navy hover:bg-sun",
    ghost: "border border-line text-paper hover:border-paper",
    danger: "border border-danger/50 text-danger hover:bg-danger hover:text-cream",
  }[variant];
  return (
    <motion.button
      whileTap={{ scale: 0.97 }}
      className={`inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-bold uppercase tracking-wide transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${styles} ${className}`}
      disabled={disabled || loading}
      {...(rest as object)}
    >
      {loading ? "Aguarde…" : children}
    </motion.button>
  );
}

const fieldBase =
  "w-full rounded-xl border border-line bg-panel px-4 py-3 text-sm text-paper placeholder:text-mute outline-none transition-colors focus:border-orange";

export function Field({ label, error, hint, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-mute">{label}</span>
      <input className={fieldBase} {...props} />
      {hint && !error && <span className="mt-1 block text-xs text-mute">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-danger">{error}</span>}
    </label>
  );
}

export function Select({ label, children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-mute">{label}</span>
      <select className={fieldBase} {...props}>
        {children}
      </select>
    </label>
  );
}

export function Notice({ kind = "error", children }: { kind?: "error" | "info" | "ok"; children: ReactNode }) {
  const c = { error: "border-danger/40 text-danger", info: "border-line text-mute", ok: "border-sun bg-sun/30 text-navy" }[kind];
  return (
    <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} role={kind === "error" ? "alert" : "status"}
      className={`rounded-xl border px-4 py-3 text-sm ${c}`}>
      {children}
    </motion.div>
  );
}

const badgeColor: Record<StatusMatch, string> = {
  SUGERIDO: "border-line text-mute",
  ENVIADO: "border-paper/40 text-paper",
  ENTREVISTA: "border-sun bg-sun/40 text-navy",
  CONTRATADO: "border-navy bg-navy text-cream",
  RECUSADO: "border-danger/40 text-danger",
};

export function StatusBadge({ status }: { status: StatusMatch }) {
  return (
    <span className={`rounded-full border px-3 py-1 text-[11px] font-bold uppercase tracking-wider ${badgeColor[status]}`}>
      {ROTULO_STATUS[status]}
    </span>
  );
}

export function ScoreBar({ score }: { score: number }) {
  return (
    <div className="flex items-center gap-3" title={`Score ${score}`}>
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-line">
        <motion.div className="h-full bg-orange" initial={{ width: 0 }} animate={{ width: `${Math.min(100, score)}%` }}
          transition={{ duration: 0.8, ease: "easeOut" }} />
      </div>
      <span className="text-xs tabular-nums text-mute">{Math.round(score)}</span>
    </div>
  );
}
