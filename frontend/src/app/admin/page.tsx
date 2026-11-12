"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { Shell } from "@/components/Shell";
import { Avatar, Button, Card, Notice, Segmented, Skeleton, Stat, spring } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { ROTULO_STATUS, type AdminOng, type Resumo, type StatusMatch } from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");
type Filtro = "pendentes" | "verificadas" | "todas";
const QUERY: Record<Filtro, string> = { pendentes: "?verificada=false", verificadas: "?verificada=true", todas: "" };
const FILTROS: { value: Filtro; label: string }[] = [
  { value: "pendentes", label: "Pendentes" }, { value: "verificadas", label: "Verificadas" }, { value: "todas", label: "Todas" },
];

const fmtCnpj = (c: string) => c.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
const fmtData = (iso: string) => new Date(iso).toLocaleDateString("pt-BR");

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

      {!resumo && !erro && (
        <div className="mb-12 grid grid-cols-2 gap-3 lg:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-28" />)}
        </div>
      )}

      {resumo && (
        <section className="mb-12 grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6" aria-label="Resumo">
          <Stat label="ONGs pendentes" value={resumo.ongsPendentes} accent={resumo.ongsPendentes > 0} />
          <Stat label="ONGs verificadas" value={resumo.ongsVerificadas} />
          <Stat label="Empresas" value={resumo.empresas} />
          <Stat label="Candidatos" value={resumo.candidatos} />
          <Stat label="Vagas abertas" value={resumo.vagasAbertas} />
          <Stat label="Contratados" value={resumo.matchesPorStatus.CONTRATADO} />
        </section>
      )}

      {resumo && (
        <section className="mb-12">
          <h2 className="title mb-4 text-3xl">Funil geral</h2>
          <div className="flex flex-wrap gap-2">
            {(Object.keys(ROTULO_STATUS) as StatusMatch[]).map((s, i) => (
              <motion.span key={s} initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} transition={{ ...spring, delay: i * 0.06 }}
                className="glass rounded-full px-4 py-2 text-sm font-medium text-mute">
                {ROTULO_STATUS[s]} <span className="ml-1.5 font-bold text-navy">{resumo.matchesPorStatus[s]}</span>
              </motion.span>
            ))}
          </div>
        </section>
      )}

      <section>
        <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
          <h2 className="title text-3xl">ONGs</h2>
          <Segmented label="Filtro de ONGs" value={filtro} onChange={setFiltro} options={FILTROS} />
        </div>

        {!ongs && <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>}

        {ongs && ongs.length === 0 && (
          <Card className="flex flex-col items-center px-6 py-14 text-center">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-sun/50 text-2xl">🎉</div>
            <p className="text-mute">{filtro === "pendentes" ? "Nenhuma ONG aguardando verificação." : "Nenhuma ONG nesta lista."}</p>
          </Card>
        )}

        <ul className="space-y-3">
          <AnimatePresence initial={false} mode="popLayout">
            {ongs?.map((o, i) => (
              <motion.li key={o.id} layout initial={{ opacity: 0, y: 18, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, x: -40, scale: 0.97 }} transition={{ ...spring, delay: Math.min(i * 0.05, 0.3) }}
                className="glass flex flex-wrap items-center gap-4 rounded-[26px] p-4 sm:p-5">
                <Avatar name={o.razaoSocial} size={48} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-lg font-semibold tracking-tight">{o.razaoSocial}</p>
                  <p className="mt-0.5 text-sm text-mute">
                    CNPJ {fmtCnpj(o.cnpj)} · CNAE {o.cnaePrincipal} · {o.nivelCapacidade.replace("_", " ")} · desde {fmtData(o.createdAt)}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`rounded-full px-3 py-1 text-xs font-semibold ${o.verified ? "bg-navy text-cream" : "bg-sun/50"}`}>
                    {o.verified ? "✓ Verificada" : "Pendente"}
                  </span>
                  {!o.verified && <Button size="sm" loading={busyId === o.id} onClick={() => alterar(o.id, "verificar")}>Verificar</Button>}
                  <AnimatePresence mode="wait" initial={false}>
                    {o.verified && confirmar !== o.id && (
                      <motion.span key="r" initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.9 }}>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmar(o.id)}>Revogar</Button>
                      </motion.span>
                    )}
                    {o.verified && confirmar === o.id && (
                      <motion.span key="c" initial={{ opacity: 0, x: 12 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 12 }}
                        className="flex flex-wrap items-center gap-2">
                        <span className="text-xs text-mute">Não entra mais em novos matches.</span>
                        <Button size="sm" variant="danger" loading={busyId === o.id} onClick={() => alterar(o.id, "revogar")}>Confirmar</Button>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmar(undefined)}>Cancelar</Button>
                      </motion.span>
                    )}
                  </AnimatePresence>
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      </section>
    </Shell>
  );
}
