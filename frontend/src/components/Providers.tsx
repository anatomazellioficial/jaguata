"use client";

import { LazyMotion, MotionConfig } from "framer-motion";
import type { ReactNode } from "react";

/** Os recursos de animação chegam num chunk à parte, depois da hidratação: o JavaScript inicial fica menor. */
const carregarRecursos = () => import("@/lib/motion-features").then((m) => m.default);

/** Respeita "reduzir movimento" do sistema: animações de transform são desligadas. */
export function Providers({ children }: { children: ReactNode }) {
  return (
    <LazyMotion features={carregarRecursos} strict>
      <MotionConfig reducedMotion="user">{children}</MotionConfig>
    </LazyMotion>
  );
}
