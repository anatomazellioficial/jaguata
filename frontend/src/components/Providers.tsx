"use client";

import { MotionConfig } from "framer-motion";
import type { ReactNode } from "react";

/** Respeita "reduzir movimento" do sistema: animações de transform são desligadas. */
export function Providers({ children }: { children: ReactNode }) {
  return <MotionConfig reducedMotion="user">{children}</MotionConfig>;
}
