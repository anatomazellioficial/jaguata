"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { CepField } from "@/components/Lookups";
import { Shell } from "@/components/Shell";
import {
  Avatar, Button, Card, Field, Notice, ScoreRing, Segmented, Select, Sheet, Skeleton, Stat, StatusBadge, spring,
} from "@/components/ui";
import { ApiError, api, getSession } from "@/lib/api";
import {
  ROTULO_CONTRATO, ROTULO_MODELO,
  type Cargo, type Empresa, type Match, type ModeloTrabalho, type NivelCargo, type TipoContrato, type Vaga,
} from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");

export default function EmpresaPage() {
  const [empresaId, setEmpresaId] = useState<string>();
  const [empresa, setEmpresa] = useState<Empresa>();
  const [cargos, setCargos] = useState<Cargo[]>([]);
  const [vagas, setVagas] = useState<Vaga[]>();
  const [erro, setErro] = useState<string>();
  const [sheet, setSheet] = useState(false);

  const carregar = useCallback(async (id: string) => {
    const [e, v] = await Promise.all([api<Empresa>(`empresas/${id}`), api<Vaga[]>(`empresas/${id}/vagas`)]);
    setEmpresa(e);
    setVagas(v);
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const s = await getSession();
        if (!s.empresaId) {
          setErro("Esta conta não está vinculada a uma empresa.");
          return;
        }
        setEmpresaId(s.empresaId);
        setCargos(await api<Cargo[]>("cardapio/cargos"));
        await carregar(s.empresaId);
      } catch (e) {
        setErro(msg(e));
      }
    })();
  }, [carregar]);

  const abertas = vagas?.filter((v) => v.status === "ABERTA").length ?? 0;

  return (
    <Shell
      title={empresa?.razaoSocial ?? "Sua empresa"}
      subtitle="Abra vagas pelo cardápio e receba candidatos chancelados pelas ONGs."
      action={empresaId && empresa ? <Button size="sm" onClick={() => setSheet(true)}>＋ Nova vaga</Button> : undefined}
    >
      {erro && <div className="mb-6"><Notice>{erro}</Notice></div>}

      {vagas && (
        <div className="mb-10 grid grid-cols-2 gap-3 sm:max-w-md">
          <Stat label="Vagas abertas" value={abertas} accent={abertas > 0} />
          <Stat label="Total de vagas" value={vagas.length} />
        </div>
      )}

      {!vagas && !erro && (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-56" /><Skeleton className="h-56" />
        </div>
      )}

      {vagas && vagas.length === 0 && (
        <Card className="flex flex-col items-center px-6 py-16 text-center">
          <motion.div animate={{ y: [0, -6, 0] }} transition={{ repeat: Infinity, duration: 3.2, ease: "easeInOut" }}
            className="mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-sun/50 text-3xl">✨</motion.div>
          <h2 className="title text-2xl">Publique a sua primeira vaga</h2>
          <p className="mt-2 max-w-sm text-mute">Escolha o cargo no cardápio e deixe o motor de match encontrar candidatos perto de você.</p>
          <Button className="mt-6" onClick={() => setSheet(true)}>＋ Nova vaga</Button>
        </Card>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <AnimatePresence initial={false}>
          {vagas?.map((v, i) => <VagaCard key={v.id} vaga={v} index={i} />)}
        </AnimatePresence>
      </div>

      {empresaId && empresa && (
        <Sheet open={sheet} onClose={() => setSheet(false)} title="Nova vaga">
          <NovaVaga empresaId={empresaId} empresa={empresa} cargos={cargos}
            onCriada={async () => { setSheet(false); await carregar(empresaId); }} />
        </Sheet>
      )}
    </Shell>
  );
}

function NovaVaga({ empresaId, empresa, cargos, onCriada }: {
  empresaId: string; empresa: Empresa; cargos: Cargo[]; onCriada: () => void;
}) {
  const [f, setF] = useState({
    nivelCargo: "ANALISTA" as NivelCargo, tipoContrato: "FIXO" as TipoContrato, modeloTrabalho: "PRESENCIAL" as ModeloTrabalho,
    cepAtuacao: "",
  });
  const [erro, setErro] = useState<string>();
  const [erros, setErros] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((s) => ({ ...s, [k]: e.target.value }));

  // Matriz de Complexidade: micro/pequena + cargo de alto escalão = rebaixado na visão da ONG
  const rebaixa = empresa.porteReceita !== "DEMAIS" && (f.nivelCargo === "DIRETOR" || f.nivelCargo === "CEO");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined); setErros({}); setLoading(true);
    try {
      await api(`empresas/${empresaId}/vagas`, {
        method: "POST",
        body: { ...f, cepAtuacao: f.cepAtuacao.replace(/\D/g, "") },   // coordenadas são obtidas no servidor, pelo CEP
      });
      onCriada();
    } catch (err) {
      setErro(msg(err));
      if (err instanceof ApiError) setErros(err.campos ?? {});
      setLoading(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Select label="Cargo (cardápio)" value={f.nivelCargo} onChange={set("nivelCargo")}>
        {cargos.map((c) => <option key={c.codigo} value={c.codigo}>{c.rotulo}</option>)}
      </Select>
      <AnimatePresence>
        {f.nivelCargo === "APRENDIZ" && (
          <motion.div key="ap" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
            <Notice kind="info">Jovem Aprendiz: apenas candidatos de 18 a 24 anos serão sugeridos.</Notice>
          </motion.div>
        )}
        {rebaixa && (
          <motion.div key="rb" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
            <Notice kind="info">Pelo porte da sua empresa ({empresa.porteReceita}), as ONGs verão esta vaga como “Coordenador/Gerente”.</Notice>
          </motion.div>
        )}
      </AnimatePresence>
      <Select label="Vínculo" value={f.tipoContrato} onChange={set("tipoContrato")}>
        {Object.entries(ROTULO_CONTRATO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </Select>
      <Select label="Modelo de trabalho" value={f.modeloTrabalho} onChange={set("modeloTrabalho")}>
        {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </Select>
      <CepField label="CEP de atuação" value={f.cepAtuacao} error={erros.cepAtuacao}
        onChange={(v) => setF((s) => ({ ...s, cepAtuacao: v }))} />
      {erro && <Notice>{erro}</Notice>}
      <Button type="submit" loading={loading} className="w-full !py-3.5">Publicar vaga</Button>
    </form>
  );
}

function VagaCard({ vaga, index }: { vaga: Vaga; index: number }) {
  const [matches, setMatches] = useState<Match[]>();
  const [raio, setRaio] = useState("20");
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState<string>();
  const [info, setInfo] = useState<string>();

  const listar = useCallback(async () => setMatches(await api<Match[]>(`vagas/${vaga.id}/matches`)), [vaga.id]);
  useEffect(() => { listar().catch(() => undefined); }, [listar]);

  async function buscar() {
    setBusy(true); setErro(undefined); setInfo(undefined);
    try {
      const novos = await api<Match[]>(`vagas/${vaga.id}/matches/sugestoes?raioKm=${raio}&limite=20`, { method: "POST" });
      setInfo(novos.length ? `${novos.length} candidato(s) sugerido(s).` : "Nenhum candidato novo dentro do raio.");
      await listar();
    } catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }

  async function mover(id: string, status: "ENTREVISTA" | "CONTRATADO" | "RECUSADO") {
    setErro(undefined);
    try { await api(`matches/${id}/status`, { method: "PATCH", body: { status } }); await listar(); }
    catch (e) { setErro(msg(e)); }
  }

  return (
    <motion.article layout initial={{ opacity: 0, y: 28, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, scale: 0.96 }}
      transition={{ ...spring, delay: Math.min(index * 0.06, 0.3) }}
      className="glass rounded-[32px] p-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="title text-2xl">{vaga.tituloExibicao}</h3>
          <div className="mt-3 flex flex-wrap gap-2 text-xs font-semibold">
            <span className="rounded-full bg-navy/[.06] px-3 py-1">{ROTULO_CONTRATO[vaga.tipoContrato]}</span>
            <span className="rounded-full bg-navy/[.06] px-3 py-1">{ROTULO_MODELO[vaga.modeloTrabalho]}</span>
            <span className="rounded-full bg-navy/[.06] px-3 py-1 text-mute">CEP {vaga.cepAtuacao}</span>
          </div>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${vaga.status === "ABERTA" ? "bg-sun/50" : "bg-navy/[.06] text-mute"}`}>
          {vaga.status === "ABERTA" ? "● Aberta" : vaga.status.charAt(0) + vaga.status.slice(1).toLowerCase()}
        </span>
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <Segmented label="Raio de busca" value={raio} onChange={setRaio}
          options={["5", "10", "20", "50", "100"].map((k) => ({ value: k, label: `${k} km` }))} />
        <Button onClick={buscar} loading={busy} disabled={vaga.status !== "ABERTA"}>Buscar candidatos</Button>
      </div>

      <div className="mt-4 space-y-2">
        {info && <Notice kind="ok">{info}</Notice>}
        {erro && <Notice>{erro}</Notice>}
      </div>

      {matches && matches.length > 0 && (
        <ul className="mt-5 divide-y divide-navy/5 overflow-hidden rounded-[22px] bg-white/70">
          <AnimatePresence initial={false}>
            {matches.map((m, i) => (
              <motion.li key={m.id} layout initial={{ opacity: 0, x: -12 }} animate={{ opacity: 1, x: 0 }} transition={{ ...spring, delay: i * 0.04 }}
                className="flex flex-wrap items-center gap-3 px-4 py-3.5">
                <Avatar name={m.candidatoNome} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold tracking-tight">{m.candidatoNome}</p>
                  <div className="mt-1"><StatusBadge status={m.status} /></div>
                </div>
                <ScoreRing score={m.matchScore} />
                <div className="flex flex-wrap items-center justify-end gap-1.5">
                  {m.status === "SUGERIDO" && <span className="text-xs text-mute">aguardando a ONG</span>}
                  {m.status === "ENVIADO" && <Button size="sm" variant="ghost" onClick={() => mover(m.id, "ENTREVISTA")}>Entrevistar</Button>}
                  {m.status === "ENTREVISTA" && <Button size="sm" onClick={() => mover(m.id, "CONTRATADO")}>Contratar</Button>}
                  {(m.status === "ENVIADO" || m.status === "ENTREVISTA" || m.status === "SUGERIDO") && (
                    <Button size="sm" variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Recusar</Button>
                  )}
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      )}
    </motion.article>
  );
}
