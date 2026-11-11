"use client";

import type { ReactNode } from "react";
import { logout } from "@/lib/api";
import { Logo } from "./Logo";
import { Button } from "./ui";

/** Moldura das áreas logadas. */
export function Shell({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-6xl px-6 pb-24">
      <header className="flex items-center justify-between py-6">
        <Logo />
        <Button variant="ghost" onClick={() => logout()}>Sair</Button>
      </header>
      <div className="mb-12 mt-8">
        <h1 className="display text-5xl sm:text-7xl">{title}</h1>
        {subtitle && <p className="mt-4 max-w-xl text-mute">{subtitle}</p>}
      </div>
      {children}
    </div>
  );
}

export function AuthFrame({ title, children, footer }: { title: string; children: ReactNode; footer: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-6 py-16">
      <Logo />
      <h1 className="display mb-10 mt-12 text-5xl">{title}</h1>
      {children}
      <p className="mt-8 text-sm text-mute">{footer}</p>
    </main>
  );
}
