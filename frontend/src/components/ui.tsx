"use client";

import { AnimatePresence, animate, motion, useDragControls } from "framer-motion";
import {
  useEffect, useId, useRef,
  type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes,
} from "react";
import { ROTULO_STATUS, type StatusMatch } from "@/lib/types";

/** Mola padrão (sensação de iOS): rápida, com pouco "quique". */
export const spring = { type: "spring", stiffness: 380, damping: 32, mass: 0.9 } as const;
export const springSoft = { type: "spring", stiffness: 220, damping: 26 } as const;
export const easeOut = [0.22, 1, 0.36, 1] as const;

/* ------------------------------------------------------------------ revelar ao rolar */
export function Reveal({ children, delay = 0, className }: { children: ReactNode; delay?: number; className?: string }) {
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: 28, filter: "blur(6px)" }}
      whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      viewport={{ once: true, margin: "-60px" }}
      transition={{ duration: 0.8, delay, ease: easeOut }}
    >
      {children}
    </motion.div>
  );
}

/* ------------------------------------------------------------------ cartão de vidro */
export function Card({ children, className = "", hover = false, ...rest }: {
  children: ReactNode; className?: string; hover?: boolean;
} & Omit<React.ComponentProps<typeof motion.div>, "children" | "className">) {
  return (
    <motion.div
      whileHover={hover ? { y: -3, scale: 1.005 } : undefined}
      transition={spring}
      className={`glass rounded-[28px] ${className}`}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

/* ------------------------------------------------------------------ botões */
type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "dark" | "ghost" | "danger";
  size?: "md" | "sm";
  loading?: boolean;
};

export function Button({ variant = "primary", size = "md", loading, className = "", children, disabled, ...rest }: BtnProps) {
  const styles = {
    primary: "bg-orange text-navy shadow-[0_6px_18px_-6px_rgba(231,139,72,.7)] hover:bg-[#ee9858]",
    dark: "bg-navy text-cream shadow-[0_6px_18px_-8px_rgba(16,46,80,.6)] hover:bg-[#1a3f6b]",
    ghost: "bg-navy/[.06] text-navy hover:bg-navy/[.11]",
    danger: "bg-brick/10 text-brick hover:bg-brick hover:text-cream",
  }[variant];
  const sizes = size === "sm" ? "px-3.5 py-1.5 text-[13px]" : "px-5 py-2.5 text-sm";
  return (
    <motion.button
      whileTap={{ scale: 0.95 }}
      whileHover={{ scale: 1.03 }}
      transition={spring}
      className={`inline-flex items-center justify-center gap-2 rounded-full font-semibold tracking-tight transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${sizes} ${styles} ${className}`}
      disabled={disabled || loading}
      {...(rest as object)}
    >
      {loading ? (
        <span className="inline-flex items-center gap-2">
          <motion.span className="h-3.5 w-3.5 rounded-full border-2 border-current border-t-transparent"
            animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 0.8, ease: "linear" }} />
          Aguarde
        </span>
      ) : children}
    </motion.button>
  );
}

/* ------------------------------------------------------------------ campos */
const fieldBase =
  "w-full rounded-2xl bg-white/80 px-4 py-3 text-[15px] text-navy outline-none transition-all duration-200 " +
  "shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)] placeholder:text-mute/60 " +
  "focus:bg-white focus:shadow-[inset_0_0_0_1.5px_#e78b48,0_0_0_5px_rgba(231,139,72,.16)]";

export function Field({ label, error, hint, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-mute">{label}</span>
      <input className={fieldBase} {...props} />
      {hint && !error && <span className="mt-1.5 block text-xs text-mute">{hint}</span>}
      {error && (
        <motion.span initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} className="mt-1.5 block text-xs text-brick">
          {error}
        </motion.span>
      )}
    </label>
  );
}

export function Select({ label, children, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { label: string }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-medium text-mute">{label}</span>
      <span className="relative block">
        <select className={`${fieldBase} appearance-none pr-10`} {...props}>{children}</select>
        <svg className="pointer-events-none absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 text-mute" viewBox="0 0 20 20" fill="none"
          stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M6 8l4 4 4-4" />
        </svg>
      </span>
    </label>
  );
}

/* ------------------------------------------------------------------ avisos */
export function Notice({ kind = "error", children }: { kind?: "error" | "info" | "ok"; children: ReactNode }) {
  const c = {
    error: "bg-brick/[.08] text-brick",
    info: "bg-navy/[.05] text-mute",
    ok: "bg-sun/30 text-navy",
  }[kind];
  const icon = { error: "!", info: "i", ok: "✓" }[kind];
  return (
    <motion.div
      initial={{ opacity: 0, y: -8, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={spring}
      role={kind === "error" ? "alert" : "status"}
      className={`flex items-start gap-3 rounded-2xl px-4 py-3 text-sm ${c}`}
    >
      <span aria-hidden className="mt-px flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-current/15 text-[11px] font-bold">
        {icon}
      </span>
      <span>{children}</span>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ status */
const badgeColor: Record<StatusMatch, string> = {
  SUGERIDO: "bg-navy/[.06] text-mute",
  ENVIADO: "bg-navy/10 text-navy",
  ENTREVISTA: "bg-sun/50 text-navy",
  CONTRATADO: "bg-navy text-cream",
  RECUSADO: "bg-brick/10 text-brick",
};

export function StatusBadge({ status }: { status: StatusMatch }) {
  return (
    <motion.span layout transition={spring}
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${badgeColor[status]}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {ROTULO_STATUS[status]}
    </motion.span>
  );
}

/* ------------------------------------------------------------------ anel de score (estilo Apple Watch) */
export function ScoreRing({ score, size = 44 }: { score: number; size?: number }) {
  const stroke = Math.max(4, size * 0.1);
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(100, score));
  const color = pct >= 70 ? "#e78b48" : pct >= 40 ? "#f5c45e" : "#9aa7b8";
  return (
    <div title={`Score ${Math.round(pct)}`} className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="rgba(16,46,80,.08)" strokeWidth={stroke} />
        <motion.circle
          cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round"
          strokeDasharray={c} initial={{ strokeDashoffset: c }} animate={{ strokeDashoffset: c * (1 - pct / 100) }}
          transition={{ duration: 1.1, ease: easeOut }} transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center font-bold tabular-nums text-navy" style={{ fontSize: size * 0.3 }}>
        {Math.round(pct)}
      </span>
    </div>
  );
}

/* ------------------------------------------------------------------ avatar com iniciais */
const GRADS = [
  "from-orange to-sun", "from-navy to-[#2c5a8f]", "from-sun to-orange", "from-[#2c5a8f] to-orange", "from-brick to-orange",
];
export function Avatar({ name, size = 40 }: { name: string; size?: number }) {
  const parts = name.trim().split(/\s+/);
  const initials = ((parts[0]?.[0] ?? "") + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
  let h = 0;
  for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  const grad = GRADS[h % GRADS.length];
  return (
    <span aria-hidden style={{ width: size, height: size, fontSize: size * 0.38 }}
      className={`flex shrink-0 items-center justify-center rounded-full bg-gradient-to-br font-semibold ${grad} ${grad.startsWith("from-sun") ? "text-navy" : "text-cream"}`}>
      {initials}
    </span>
  );
}

/* ------------------------------------------------------------------ controle segmentado (indicador desliza) */
export function Segmented<T extends string>({ options, value, onChange, label }: {
  options: { value: T; label: string }[]; value: T; onChange: (v: T) => void; label?: string;
}) {
  const id = useId();
  return (
    <div className="no-scrollbar max-w-full overflow-x-auto">
      <div role="tablist" aria-label={label} className="relative inline-flex rounded-full bg-navy/[.06] p-1">
        {options.map((o) => {
          const active = o.value === value;
          return (
            <button key={o.value} role="tab" aria-selected={active} onClick={() => onChange(o.value)}
              className={`relative z-10 whitespace-nowrap rounded-full px-4 py-1.5 text-[13px] font-medium transition-colors ${active ? "text-navy" : "text-mute hover:text-navy"}`}>
              {active && (
                <motion.span layoutId={`seg-${id}`} transition={spring} className="absolute inset-0 -z-10 rounded-full bg-white shadow-soft" />
              )}
              {o.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ sheet (sobe da base, arrasta para fechar) */
export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const controls = useDragControls();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    ref.current?.focus();
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = prev; };
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.25 }}>
          <div onClick={onClose} aria-hidden className="absolute inset-0 bg-navy/30 backdrop-blur-[6px]" />
          <motion.div
            ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-label={title}
            drag="y" dragControls={controls} dragListener={false} dragConstraints={{ top: 0, bottom: 0 }} dragElastic={{ top: 0, bottom: 0.5 }}
            onDragEnd={(_, i) => { if (i.offset.y > 120 || i.velocity.y > 600) onClose(); }}
            initial={{ y: "100%", opacity: 0.6 }} animate={{ y: 0, opacity: 1 }} exit={{ y: "100%", opacity: 0 }}
            transition={{ type: "spring", stiffness: 320, damping: 34 }}
            className="glass-strong relative z-10 flex max-h-[92vh] w-full max-w-lg flex-col overflow-hidden rounded-t-[32px] outline-none sm:rounded-[32px]"
          >
            <div onPointerDown={(e) => controls.start(e)} className="flex cursor-grab touch-none justify-center pb-1 pt-3 active:cursor-grabbing sm:hidden">
              <span className="h-1.5 w-10 rounded-full bg-navy/15" />
            </div>
            <div className="flex items-center justify-between px-6 pb-2 pt-3 sm:pt-6">
              <h2 className="title text-2xl">{title}</h2>
              <motion.button whileTap={{ scale: 0.9 }} onClick={onClose} aria-label="Fechar"
                className="flex h-8 w-8 items-center justify-center rounded-full bg-navy/[.07] text-mute hover:bg-navy/[.12]">
                ✕
              </motion.button>
            </div>
            <div className="overflow-y-auto px-6 pb-7 pt-2">{children}</div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/* ------------------------------------------------------------------ skeleton e contador */
export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`shimmer rounded-2xl ${className}`} />;
}

export function CountUp({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const c = animate(0, value, {
      duration: 1, ease: easeOut,
      onUpdate: (v) => { if (ref.current) ref.current.textContent = Math.round(v).toLocaleString("pt-BR"); },
    });
    return () => c.stop();
  }, [value]);
  return <span ref={ref}>0</span>;
}

/** Bloco de métrica: número grande + legenda. */
export function Stat({ label, value, accent = false }: { label: string; value: number; accent?: boolean }) {
  return (
    <motion.div whileHover={{ y: -3 }} transition={spring}
      className={`glass rounded-[24px] p-5 ${accent ? "ring-2 ring-orange/40" : ""}`}>
      <p className={`title text-4xl tabular-nums ${accent ? "text-orange" : ""}`}><CountUp value={value} /></p>
      <p className="mt-1.5 text-[13px] font-medium text-mute">{label}</p>
    </motion.div>
  );
}
