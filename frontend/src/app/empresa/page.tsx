"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { Shell } from "@/components/Shell";
import { Button, Field, Notice, ScoreBar, Select, StatusBadge } from "@/components/ui";
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
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [erro, setErro] = useState<string>();

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

  return (
    <Shell title={empresa?.razaoSocial ?? "Sua empresa"} subtitle="Abra vagas pelo cardápio e receba candidatos chancelados pelas ONGs.">
      {erro && <Notice>{erro}</Notice>}
      {empresaId && empresa && (
        <div className="grid gap-12 lg:grid-cols-[380px_1fr]">
          <NovaVaga empresaId={empresaId} empresa={empresa} cargos={cargos} onCriada={() => carregar(empresaId)} />
          <section>
            <h2 className="display mb-6 text-3xl">Vagas ({vagas.length})</h2>
            {vagas.length === 0 && <Notice kind="info">Nenhuma vaga ainda. Crie a primeira ao lado.</Notice>}
            <div className="space-y-4">
              <AnimatePresence initial={false}>
                {vagas.map((v) => <VagaCard key={v.id} vaga={v} />)}
              </AnimatePresence>
            </div>
          </section>
        </div>
      )}
    </Shell>
  );
}

function NovaVaga({ empresaId, empresa, cargos, onCriada }: {
  empresaId: string; empresa: Empresa; cargos: Cargo[]; onCriada: () => void;
}) {
  const [f, setF] = useState({
    nivelCargo: "ANALISTA" as NivelCargo, tipoContrato: "FIXO" as TipoContrato, modeloTrabalho: "PRESENCIAL" as ModeloTrabalho,
    cepAtuacao: "", lat: "", lng: "",
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
        body: { ...f, cepAtuacao: f.cepAtuacao.replace(/\D/g, ""), lat: Number(f.lat), lng: Number(f.lng) },
      });
      setF((s) => ({ ...s, cepAtuacao: "", lat: "", lng: "" }));
      onCriada();
    } catch (err) {
      setErro(msg(err));
      if (err instanceof ApiError) setErros(err.campos ?? {});
    } finally {
      setLoading(false);
    }
  }

  function geolocalizar() {
    navigator.geolocation?.getCurrentPosition((p) =>
      setF((s) => ({ ...s, lat: p.coords.latitude.toFixed(6), lng: p.coords.longitude.toFixed(6) })));
  }

  return (
    <form onSubmit={submit} className="h-fit space-y-4 rounded-3xl border border-line bg-panel p-6">
      <h2 className="display text-3xl">Nova vaga</h2>
      <Select label="Cargo (cardápio)" value={f.nivelCargo} onChange={set("nivelCargo")}>
        {cargos.map((c) => <option key={c.codigo} value={c.codigo}>{c.rotulo}</option>)}
      </Select>
      <AnimatePresence>
        {f.nivelCargo === "APRENDIZ" && (
          <motion.div key="ap" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}>
            <Notice kind="info">Jovem Aprendiz: apenas candidatos de 18 a 24 anos serão sugeridos.</Notice>
          </motion.div>
        )}
        {rebaixa && (
          <motion.div key="rb" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}>
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
      <Field label="CEP de atuação" inputMode="numeric" required value={f.cepAtuacao} onChange={set("cepAtuacao")} error={erros.cepAtuacao} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Latitude" required inputMode="decimal" value={f.lat} onChange={set("lat")} error={erros.lat} />
        <Field label="Longitude" required inputMode="decimal" value={f.lng} onChange={set("lng")} error={erros.lng} />
      </div>
      <button type="button" onClick={geolocalizar} className="text-xs text-brick underline">Usar minha localização atual</button>
      {erro && <Notice>{erro}</Notice>}
      <Button type="submit" loading={loading} className="w-full">Publicar vaga</Button>
    </form>
  );
}

function VagaCard({ vaga }: { vaga: Vaga }) {
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
    <motion.article layout initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
      className="rounded-3xl border border-line bg-panel p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-2xl font-extrabold">{vaga.tituloExibicao}</h3>
          <p className="mt-1 text-sm text-mute">
            {ROTULO_CONTRATO[vaga.tipoContrato]} · {ROTULO_MODELO[vaga.modeloTrabalho]} · CEP {vaga.cepAtuacao}
          </p>
        </div>
        <span className="rounded-full border border-line px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-mute">{vaga.status}</span>
      </div>

      <div className="mt-5 flex flex-wrap items-end gap-3">
        <div className="w-32">
          <Select label="Raio" value={raio} onChange={(e) => setRaio(e.target.value)}>
            {[5, 10, 20, 50, 100].map((k) => <option key={k} value={k}>{k} km</option>)}
          </Select>
        </div>
        <Button onClick={buscar} loading={busy} disabled={vaga.status !== "ABERTA"}>Buscar candidatos</Button>
      </div>

      <div className="mt-4 space-y-2">
        {info && <Notice kind="ok">{info}</Notice>}
        {erro && <Notice>{erro}</Notice>}
      </div>

      {matches && matches.length > 0 && (
        <ul className="mt-6 divide-y divide-line border-t border-line">
          {matches.map((m) => (
            <li key={m.id} className="flex flex-wrap items-center justify-between gap-3 py-4">
              <div>
                <p className="font-semibold">{m.candidatoNome}</p>
                <ScoreBar score={m.matchScore} />
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={m.status} />
                {m.status === "SUGERIDO" && <span className="text-xs text-mute">aguardando envio da ONG</span>}
                {m.status === "ENVIADO" && <Button variant="ghost" onClick={() => mover(m.id, "ENTREVISTA")}>Entrevistar</Button>}
                {m.status === "ENTREVISTA" && <Button onClick={() => mover(m.id, "CONTRATADO")}>Contratar</Button>}
                {(m.status === "ENVIADO" || m.status === "ENTREVISTA" || m.status === "SUGERIDO") && (
                  <Button variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Recusar</Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </motion.article>
  );
}
