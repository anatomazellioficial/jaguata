"use client";

import { AnimatePresence, animate, motion } from "framer-motion";
import { useCallback, useEffect, useRef, useState } from "react";
import { Shell } from "@/components/Shell";
import { Button, Notice } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { ROTULO_STATUS, type AdminOng, type Resumo, type StatusMatch } from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");
type Filtro = "pendentes" | "verificadas" | "todas";
const QUERY: Record<Filtro, string> = { pendentes: "?verificada=false", verificadas: "?verificada=true", todas: "" };

const fmtCnpj = (c: string) => c.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
const fmtData = (iso: string) => new Date(iso).toLocaleDateString("pt-BR");

function Contador({ valor }: { valor: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const c = animate(0, valor, {
      duration: 0.9, ease: "easeOut",
      onUpdate: (v) => { if (ref.current) ref.current.textContent = Math.round(v).toLocaleString("pt-BR"); },
    });
    return () => c.stop();
  }, [valor]);
  return <span ref={ref}>0</span>;
}

function Card({ rotulo, valor, destaque }: { rotulo: string; valor: number; destaque?: boolean }) {
  return (
    <div className={`rounded-2xl border p-5 ${destaque ? "border-neon/50" : "border-line"} bg-panel`}>
      <p className={`display text-5xl ${destaque ? "text-brick" : ""}`}><Contador valor={valor} /></p>
      <p className="mt-2 text-xs font-semibold uppercase tracking-wider text-mute">{rotulo}</p>
    </div>
  );
}

export default function AdminPage() {
  const [resumo, setResumo] = useState<Resumo>();
  const [filtro, setFiltro] = useState<Filtro>("pendentes");
  const [ongs, setOngs] = useState<AdminOng[]>();
  const [erro, setErro] = useState<string>();
  const [busyId, setBusyId] = useState<string>();
  const [confirmar, setConfirmar] = useState<string>();

  const carregarResumo = useCallback(async () => setResumo(await api<Resumo>("admin/resumo")), []);
  const carregarOngs = useCallback(async () => setOngs(await api<AdminOng[]>(`admin/ongs${QUERY[filtro]}`)), [filtro]);

  useEffect(() => { carregarResumo().catch((e) => setErro(msg(e))); }, [carregarResumo]);
  useEffect(() => { setOngs(undefined); carregarOngs().catch((e) => setErro(msg(e))); }, [carregarOngs]);

  async function alterar(id: string, acao: "verificar" | "revogar") {
    setBusyId(id); setErro(undefined); setConfirmar(undefined);
    try {
      await api(`admin/ongs/${id}/${acao}`, { method: "POST" });
      await Promise.all([carregarOngs(), carregarResumo()]);
    } catch (e) { setErro(msg(e)); } finally { setBusyId(undefined); }
  }

  return (
    <Shell title="Administração" subtitle="Verifique as ONGs que podem enviar candidatos e acompanhe a saúde da plataforma.">
      {erro && <div className="mb-6"><Notice>{erro}</Notice></div>}

      {resumo && (
        <section className="mb-14 grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6" aria-label="Resumo">
          <Card rotulo="ONGs pendentes" valor={resumo.ongsPendentes} destaque={resumo.ongsPendentes > 0} />
          <Card rotulo="ONGs verificadas" valor={resumo.ongsVerificadas} />
          <Card rotulo="Empresas" valor={resumo.empresas} />
          <Card rotulo="Candidatos" valor={resumo.candidatos} />
          <Card rotulo="Vagas abertas" valor={resumo.vagasAbertas} />
          <Card rotulo="Contratados" valor={resumo.matchesPorStatus.CONTRATADO} />
        </section>
      )}

      {resumo && (
        <section className="mb-14">
          <h2 className="display mb-4 text-3xl">Funil geral</h2>
          <div className="flex flex-wrap gap-2">
            {(Object.keys(ROTULO_STATUS) as StatusMatch[]).map((s) => (
              <span key={s} className="rounded-full border border-line px-4 py-2 text-xs font-bold uppercase tracking-wider text-mute">
                {ROTULO_STATUS[s]} <span className="ml-1 text-paper">{resumo.matchesPorStatus[s]}</span>
              </span>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="display mb-6 text-3xl">ONGs</h2>
        <div className="mb-6 flex gap-2" role="tablist">
          {(Object.keys(QUERY) as Filtro[]).map((f) => (
            <button key={f} role="tab" aria-selected={filtro === f} onClick={() => setFiltro(f)}
              className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-wider transition-colors ${filtro === f ? "border-neon bg-neon text-navy" : "border-line text-mute hover:text-paper"}`}>
              {f}
            </button>
          ))}
        </div>

        {ongs && ongs.length === 0 && (
          <Notice kind="info">{filtro === "pendentes" ? "Nenhuma ONG aguardando verificação. 🎉" : "Nenhuma ONG nesta lista."}</Notice>
        )}

        <ul className="space-y-3">
          <AnimatePresence initial={false}>
            {ongs?.map((o) => (
              <motion.li key={o.id} layout initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: -24 }}
                className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-panel p-5">
                <div>
                  <p className="text-lg font-bold">{o.razaoSocial}</p>
                  <p className="mt-1 text-sm text-mute">
                    CNPJ {fmtCnpj(o.cnpj)} · CNAE {o.cnaePrincipal} · {o.nivelCapacidade.replace("_", " ")} · CEP {o.cep} · desde {fmtData(o.createdAt)}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`rounded-full border px-3 py-1 text-[11px] font-bold uppercase tracking-wider ${o.verified ? "border-sun bg-sun/40 text-navy" : "border-line text-mute"}`}>
                    {o.verified ? "Verificada" : "Pendente"}
                  </span>
                  {!o.verified && (
                    <Button loading={busyId === o.id} onClick={() => alterar(o.id, "verificar")}>Verificar</Button>
                  )}
                  {o.verified && confirmar !== o.id && (
                    <Button variant="ghost" onClick={() => setConfirmar(o.id)}>Revogar</Button>
                  )}
                  {o.verified && confirmar === o.id && (
                    <>
                      <span className="text-xs text-mute">Não entra mais em novos matches.</span>
                      <Button variant="danger" loading={busyId === o.id} onClick={() => alterar(o.id, "revogar")}>Confirmar</Button>
                      <Button variant="ghost" onClick={() => setConfirmar(undefined)}>Cancelar</Button>
                    </>
                  )}
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      </section>
    </Shell>
  );
}
