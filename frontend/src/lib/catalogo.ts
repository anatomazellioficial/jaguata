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

let cacheSkills: string[] | undefined;

/** Habilidades sugeridas (catálogo do servidor); o texto livre continua permitido. */
export function useSkills() {
  const [c, setC] = useState<string[] | undefined>(cacheSkills);
  useEffect(() => {
    if (cacheSkills) return;
    api<string[]>("cardapio/skills").then((r) => { cacheSkills = r; setC(r); }).catch(() => undefined);
  }, []);
  return c ?? [];
}

let cacheRecursos: { filtroIdade: boolean } | undefined;

/** Recursos que dependem da configuração da instalação (ex.: filtro de idade, desligado por padrão). */
export function useRecursos() {
  const [r, setR] = useState<{ filtroIdade: boolean } | undefined>(cacheRecursos);
  useEffect(() => {
    if (cacheRecursos) return;
    api<{ filtroIdade: boolean }>("cardapio/recursos").then((x) => { cacheRecursos = x; setR(x); }).catch(() => undefined);
  }, []);
  return r;
}
