import Link from "next/link";

/** Símbolo "J": gancho externo em currentColor + traço interno laranja. */
export function LogoMark({ className = "h-9 w-auto" }: { className?: string }) {
  return (
    <svg viewBox="0 0 60 72" className={className} fill="none" strokeWidth="7" strokeLinecap="round" aria-hidden>
      <path d="M52 5 V37 A22 22 0 0 1 8 37 V33" stroke="currentColor" />
      <path d="M40 19 V37 A10 10 0 0 1 20 37 V35" stroke="#e78b48" />
    </svg>
  );
}

/** Texto "jaguatá" em Lexend, com o acento agudo em laranja. */
export function Wordmark({ className = "text-2xl" }: { className?: string }) {
  return (
    <span className={`font-extrabold lowercase leading-none tracking-tight ${className}`}>
      jaguat
      <span className="relative inline-block">
        a
        <i
          aria-hidden
          className="absolute block rounded-full bg-orange"
          style={{ width: "0.11em", height: "0.3em", left: "0.5em", top: "-0.2em", transform: "rotate(38deg)" }}
        />
      </span>
    </span>
  );
}

/**
 * Logo completa. `tone="dark"` = azul-marinho (sobre creme); `tone="light"` = creme (sobre azul-marinho).
 */
export function Logo({
  tone = "dark",
  href = "/",
  size = "md",
}: {
  tone?: "dark" | "light";
  href?: string | null;
  size?: "md" | "lg";
}) {
  const color = tone === "dark" ? "text-navy" : "text-cream";
  const content = (
    <span className={`inline-flex items-center gap-3 ${color}`} role="img" aria-label="Jaguatá">
      <LogoMark className={size === "lg" ? "h-14 w-auto" : "h-9 w-auto"} />
      <Wordmark className={size === "lg" ? "text-5xl" : "text-2xl"} />
    </span>
  );
  return href ? <Link href={href} aria-label="Jaguatá — início">{content}</Link> : content;
}
