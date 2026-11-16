"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Catalogo } from "@/lib/types";

let cache: Catalogo | undefined;

/** Catálogos (escolaridade, regime, setores, grupos…) vindos do servidor: a fonte da verdade dos rótulos. */
export function useCatalogo() {
  const [c, setC] = useState<Catalogo | undefined>(cache);
  useEffect(() => {
    if (cache) return;
    api<Catalogo>("cardapio/perfil").then((r) => { cache = r; setC(r); }).catch(() => undefined);
  }, []);
  return c;
}
