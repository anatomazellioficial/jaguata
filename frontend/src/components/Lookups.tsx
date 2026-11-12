"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useRef, useState } from "react";
import { ApiError, api } from "@/lib/api";
import type { ConsultaCep, ConsultaCnpj } from "@/lib/types";
import { Field, Notice, spring } from "./ui";

export type Estado = "vazio" | "carregando" | "ok" | "erro";

export const soDigitos = (v: string) => v.replace(/\D/g, "");

export function mascaraCnpj(v: string) {
  const d = soDigitos(v).slice(0, 14);
  return d
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}

export function mascaraCep(v: string) {
  const d = soDigitos(v).slice(0, 8);
  return d.length > 5 ? `${d.slice(0, 5)}-${d.slice(5)}` : d;
}

/**
 * Consulta um endpoint público (/consulta/...) quando o valor tem o tamanho esperado.
 * Com debounce e descarte de respostas atrasadas (só a última digitação vale).
 */
function useConsulta<T>(rota: "cnpj" | "cep", digitos: string, tamanho: number) {
  const [estado, setEstado] = useState<Estado>("vazio");
  const [dado, setDado] = useState<T>();
  const [erro, setErro] = useState<string>();
  const ultima = useRef(0);

  useEffect(() => {
    const id = ++ultima.current;
    setDado(undefined);
    setErro(undefined);
    if (digitos.length !== tamanho) {
      setEstado("vazio");
      return;
    }
    setEstado("carregando");
    const t = setTimeout(async () => {
      try {
        const r = await api<T>(`consulta/${rota}/${digitos}`);
        if (id !== ultima.current) return;
        setDado(r);
        setEstado("ok");
      } catch (e) {
        if (id !== ultima.current) return;
        setErro(e instanceof ApiError ? e.message : "Não foi possível consultar agora.");
        setEstado("erro");
      }
    }, 350);
    return () => clearTimeout(t);
  }, [rota, digitos, tamanho]);

  return { estado, dado, erro };
}

function Spinner() {
  return (
    <motion.span aria-hidden className="inline-block h-3.5 w-3.5 rounded-full border-2 border-orange border-t-transparent"
      animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 0.8, ease: "linear" }} />
  );
}

/* ------------------------------------------------------------------ CEP */
export function CepField({ label, value, onChange, error, onEstado }: {
  label: string; value: string; onChange: (v: string) => void; error?: string; onEstado?: (e: Estado) => void;
}) {
  const digitos = soDigitos(value);
  const { estado, dado, erro } = useConsulta<ConsultaCep>("cep", digitos, 8);

  useEffect(() => { onEstado?.(estado); }, [estado, onEstado]);

  return (
    <div className="space-y-2">
      <Field label={label} inputMode="numeric" autoComplete="postal-code" required placeholder="00000-000"
        value={mascaraCep(value)} onChange={(e) => onChange(soDigitos(e.target.value).slice(0, 8))} error={error} />
      <AnimatePresence mode="wait" initial={false}>
        {estado === "carregando" && (
          <motion.p key="l" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="flex items-center gap-2 text-xs text-mute"><Spinner /> Buscando endereço…</motion.p>
        )}
        {estado === "ok" && dado && (
          <motion.div key="ok" initial={{ opacity: 0, y: -6, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0 }}
            transition={spring} className="flex items-start gap-3 rounded-2xl bg-sun/30 px-4 py-3 text-sm">
            <span aria-hidden className="mt-0.5">📍</span>
            <div>
              <p className="font-semibold">{[dado.logradouro, dado.bairro].filter(Boolean).join(", ") || "Endereço localizado"}</p>
              <p className="text-mute">{dado.cidade}/{dado.uf}</p>
              {dado.precisao !== "CEP" && (
                <p className="mt-1 text-xs text-mute">
                  {dado.precisao === "RUA" ? "Localização aproximada pela rua." : "Localização aproximada pelo centro da cidade."}
                </p>
              )}
            </div>
          </motion.div>
        )}
        {estado === "erro" && (
          <motion.div key="e" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            <Notice>{erro}</Notice>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ------------------------------------------------------------------ CNPJ */
const ROTULO_PORTE = { MEI: "MEI", ME: "Microempresa (ME)", EPP: "Empresa de pequeno porte (EPP)", DEMAIS: "Demais portes" } as const;

/** Campo de CNPJ com pré-visualização dos dados da Receita. `onResultado(true)` quando pode prosseguir. */
export function CnpjField({ tipo, value, onChange, error, onPodeCadastrar }: {
  tipo: "empresa" | "ong"; value: string; onChange: (v: string) => void; error?: string;
  onPodeCadastrar: (ok: boolean) => void;
}) {
  const digitos = soDigitos(value);
  const { estado, dado, erro } = useConsulta<ConsultaCnpj>("cnpj", digitos, 14);

  useEffect(() => { onPodeCadastrar(estado === "ok" && !!dado?.ativa); }, [estado, dado, onPodeCadastrar]);

  return (
    <div className="space-y-3">
      <Field label="CNPJ" inputMode="numeric" required placeholder="00.000.000/0000-00"
        value={mascaraCnpj(value)} onChange={(e) => onChange(soDigitos(e.target.value).slice(0, 14))}
        error={error} hint="Buscamos os dados na Receita Federal; você só confirma." />
      <AnimatePresence mode="wait" initial={false}>
        {estado === "carregando" && (
          <motion.p key="l" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="flex items-center gap-2 text-xs text-mute"><Spinner /> Consultando a Receita Federal…</motion.p>
        )}
        {estado === "erro" && (
          <motion.div key="e" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            <Notice>{erro}</Notice>
          </motion.div>
        )}
        {estado === "ok" && dado && (
          <motion.div key="ok" initial={{ opacity: 0, y: -8, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0 }}
            transition={spring} className="overflow-hidden rounded-3xl bg-white/80 shadow-[inset_0_0_0_1px_rgba(16,46,80,.08)]">
            <div className="flex items-start justify-between gap-3 p-4">
              <div className="min-w-0">
                <p className="text-xs font-medium text-mute">Receita Federal</p>
                <p className="mt-0.5 text-[17px] font-semibold leading-snug tracking-tight">{dado.razaoSocial}</p>
                {dado.nomeFantasia && <p className="text-sm text-mute">{dado.nomeFantasia}</p>}
              </div>
              <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold ${dado.ativa ? "bg-navy text-cream" : "bg-brick/10 text-brick"}`}>
                {dado.ativa ? "✓ Ativa" : dado.situacao || "Inativa"}
              </span>
            </div>
            <dl className="grid grid-cols-2 gap-px bg-navy/5 text-sm">
              <div className="bg-white/80 p-3.5">
                <dt className="text-xs text-mute">CNAE principal</dt>
                <dd className="mt-0.5 font-medium tabular-nums">{dado.cnaePrincipal}</dd>
              </div>
              <div className="bg-white/80 p-3.5">
                <dt className="text-xs text-mute">{tipo === "empresa" ? "Porte" : "Natureza jurídica"}</dt>
                <dd className="mt-0.5 font-medium">{tipo === "empresa" ? ROTULO_PORTE[dado.porte] : dado.naturezaJuridica || "—"}</dd>
              </div>
              <div className="col-span-2 bg-white/80 p-3.5">
                <dt className="text-xs text-mute">Sede</dt>
                <dd className="mt-0.5 font-medium">
                  {[dado.logradouro, dado.bairro].filter(Boolean).join(", ")} · {dado.municipio}/{dado.uf}
                </dd>
              </div>
            </dl>
            {!dado.ativa && (
              <div className="p-3"><Notice>Só é possível cadastrar CNPJs com situação cadastral ativa.</Notice></div>
            )}
            {dado.ativa && tipo === "ong" && !dado.entidadeSemFinsLucrativos && (
              <div className="p-3">
                <Notice kind="info">Este CNPJ não consta como entidade sem fins lucrativos. Você pode se cadastrar, mas a verificação pode ser negada.</Notice>
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
