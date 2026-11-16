"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { CepField } from "@/components/Lookups";
import { Shell } from "@/components/Shell";
import {
  Avatar, Button, Card, ChipGroup, Field, Notice, ScoreRing, Segmented, Select, Sheet, Skeleton, Stat, StatusBadge, spring,
} from "@/components/ui";
import { useCatalogo } from "@/lib/catalogo";
import { ApiError, api, getSession } from "@/lib/api";
import {
  ROTULO_CONTRATO, ROTULO_CRITERIO, ROTULO_MODELO,
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
      mobileAction={empresaId && empresa ? { label: "Nova vaga", onClick: () => setSheet(true) } : undefined}
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
    cepAtuacao: "", escolaridadeMinima: "", regime: "",
  });
  const cat = useCatalogo();
  const [skills, setSkills] = useState<string[]>([]);
  const [novaSkill, setNovaSkill] = useState("");
  const [afirmativa, setAfirmativa] = useState(false);
  const [grupos, setGrupos] = useState<string[]>([]);
  const [justificativa, setJustificativa] = useState("");
  const [erro, setErro] = useState<string>();
  const [erros, setErros] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((s) => ({ ...s, [k]: e.target.value }));

  // Matriz de Complexidade: micro/pequena + cargo de alto escalão = rebaixado na visão da ONG
  const rebaixa = empresa.porteReceita !== "DEMAIS" && (f.nivelCargo === "DIRETOR" || f.nivelCargo === "CEO");

  function addSkill() {
    const t = novaSkill.trim();
    if (!t || skills.length >= 15 || skills.some((x) => x.toLowerCase() === t.toLowerCase())) return;
    setSkills((s) => [...s, t]);
    setNovaSkill("");
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined); setErros({}); setLoading(true);
    try {
      await api(`empresas/${empresaId}/vagas`, {
        method: "POST",
        body: {
          ...f, cepAtuacao: f.cepAtuacao.replace(/\D/g, ""),   // coordenadas são obtidas no servidor, pelo CEP
          escolaridadeMinima: f.escolaridadeMinima || null, regime: f.regime || null, skills,
          gruposPrioritarios: afirmativa ? grupos : [],
          justificativaAfirmativa: afirmativa ? justificativa : null,
        },
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

      <div className="grid gap-4 sm:grid-cols-2">
        <Select label="Escolaridade mínima" value={f.escolaridadeMinima} onChange={set("escolaridadeMinima")}>
          <option value="">Sem exigência</option>
          {cat?.escolaridades.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
        </Select>
        <Select label="Regime de contratação" value={f.regime} onChange={set("regime")}>
          <option value="">Qualquer</option>
          {cat?.regimes.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
        </Select>
      </div>
      {f.nivelCargo === "ESTAGIARIO" && (
        <Notice kind="info">Estágio: só são sugeridos candidatos com curso técnico ou superior em andamento.</Notice>
      )}

      <fieldset className="space-y-3">
        <legend className="mb-1.5 text-[13px] font-medium text-mute">Habilidades desejadas (até 15)</legend>
        <div className="flex gap-2">
          <input aria-label="Habilidade" placeholder="Excel, atendimento, CNH B…" value={novaSkill}
            onChange={(e) => setNovaSkill(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addSkill(); } }}
            className="min-w-0 flex-1 rounded-2xl bg-white/80 px-4 py-2.5 text-sm shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)] outline-none focus:shadow-[inset_0_0_0_1.5px_#e78b48]" />
          <Button type="button" variant="ghost" onClick={addSkill}>＋</Button>
        </div>
        <div className="flex flex-wrap gap-2">
          {skills.map((t) => (
            <button key={t} type="button" onClick={() => setSkills((s) => s.filter((x) => x !== t))} title="Remover"
              className="rounded-full bg-sun/50 px-3 py-1 text-xs font-semibold hover:bg-brick/10 hover:text-brick">{t} ✕</button>
          ))}
        </div>
      </fieldset>

      <div className="rounded-2xl bg-navy/[.04] p-4">
        <label className="flex cursor-pointer items-start gap-3 text-sm">
          <input type="checkbox" checked={afirmativa} onChange={(e) => setAfirmativa(e.target.checked)}
            className="mt-0.5 h-5 w-5 shrink-0 accent-[#e78b48]" />
          <span>
            <b>Vaga afirmativa</b> (opcional)
            <span className="block text-mute">Candidatos dos grupos escolhidos ganham pontos extras no ranking. Ninguém é excluído, e a
              autodeclaração dos candidatos nunca é exibida à empresa.</span>
          </span>
        </label>
        <AnimatePresence initial={false}>
          {afirmativa && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
              className="mt-4 space-y-4 overflow-hidden">
              <ChipGroup label="Grupos priorizados" options={cat?.grupos ?? []} value={grupos} onChange={setGrupos} />
              <label className="block text-[13px] font-medium text-mute">
                Justificativa da ação afirmativa (mín. 20 caracteres)
                <textarea value={justificativa} onChange={(e) => setJustificativa(e.target.value)} rows={3} maxLength={500}
                  className="mt-1.5 w-full rounded-2xl bg-white/80 px-4 py-2.5 text-sm text-navy shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)] outline-none focus:shadow-[inset_0_0_0_1.5px_#e78b48]" />
              </label>
              {erros.justificativaAfirmativa && <Notice>{erros.justificativaAfirmativa}</Notice>}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
      {erro && <Notice>{erro}</Notice>}
      <Button type="submit" loading={loading} className="w-full !py-3.5">Publicar vaga</Button>
    </form>
  );
}

function VagaCard({ vaga, index }: { vaga: Vaga; index: number }) {
  const [matches, setMatches] = useState<Match[]>();
  const [raio, setRaio] = useState("20");
  const [filtrosAbertos, setFiltrosAbertos] = useState(false);
  const [fModelo, setFModelo] = useState("");
  const [fEsc, setFEsc] = useState("");
  const [fRegime, setFRegime] = useState("");
  const [fSkills, setFSkills] = useState("");
  const [aberto, setAberto] = useState<string>();
  const cat = useCatalogo();
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState<string>();
  const [info, setInfo] = useState<string>();

  const listar = useCallback(async () => setMatches(await api<Match[]>(`vagas/${vaga.id}/matches`)), [vaga.id]);
  useEffect(() => { listar().catch(() => undefined); }, [listar]);

  async function buscar() {
    setBusy(true); setErro(undefined); setInfo(undefined);
    try {
      const q = new URLSearchParams({ raioKm: raio, limite: "20" });
      if (fModelo) q.set("modelo", fModelo);
      if (fEsc) q.set("escolaridadeMin", fEsc);
      if (fRegime) q.set("regime", fRegime);
      if (fSkills.trim()) q.set("skills", fSkills.trim());
      const novos = await api<Match[]>(`vagas/${vaga.id}/matches/sugestoes?${q}`, { method: "POST" });
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
            {vaga.escolaridadeMinima && <span className="rounded-full bg-navy/[.06] px-3 py-1">{cat?.escolaridades.find((o) => o.codigo === vaga.escolaridadeMinima)?.rotulo ?? vaga.escolaridadeMinima}+</span>}
            {vaga.regime && <span className="rounded-full bg-navy/[.06] px-3 py-1">{vaga.regime}</span>}
            {vaga.gruposPrioritarios.length > 0 && <span className="rounded-full bg-sun/60 px-3 py-1">Vaga afirmativa</span>}
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

      <div className="mt-3">
        <button type="button" onClick={() => setFiltrosAbertos((v) => !v)} aria-expanded={filtrosAbertos}
          className="text-xs font-semibold text-brick underline">
          {filtrosAbertos ? "Ocultar filtros" : "Filtros da busca"}
        </button>
        <AnimatePresence initial={false}>
          {filtrosAbertos && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
              className="overflow-hidden">
              <div className="mt-3 grid gap-3 rounded-[22px] bg-white/60 p-4 sm:grid-cols-2">
                <Select label="Modelo preferido" value={fModelo} onChange={(e) => setFModelo(e.target.value)}>
                  <option value="">Qualquer</option>
                  {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </Select>
                <Select label="Escolaridade mínima" value={fEsc} onChange={(e) => setFEsc(e.target.value)}>
                  <option value="">Qualquer</option>
                  {cat?.escolaridades.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
                </Select>
                <Select label="Regime" value={fRegime} onChange={(e) => setFRegime(e.target.value)}>
                  <option value="">Qualquer</option>
                  {cat?.regimes.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
                </Select>
                <Field label="Habilidades obrigatórias" placeholder="excel, atendimento" value={fSkills}
                  onChange={(e) => setFSkills(e.target.value)} hint="Separe por vírgula." />
              </div>
              <p className="mt-2 text-xs text-mute">Filtros usam só critérios profissionais e excluem quem não atende.</p>
            </motion.div>
          )}
        </AnimatePresence>
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
                  <div className="mt-1 flex items-center gap-2">
                    <StatusBadge status={m.status} />
                    {m.motivos?.length > 0 && (
                      <button type="button" aria-expanded={aberto === m.id} onClick={() => setAberto(aberto === m.id ? undefined : m.id)}
                        className="text-xs font-semibold text-brick underline">Por quê?</button>
                    )}
                  </div>
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
                <AnimatePresence initial={false}>
                  {aberto === m.id && (
                    <motion.ul initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
                      className="basis-full space-y-1.5 overflow-hidden pl-[52px] text-xs">
                      {m.motivos.map((x) => (
                        <li key={x.criterio} className="flex items-center gap-2">
                          <span className="w-36 shrink-0 font-semibold">{ROTULO_CRITERIO[x.criterio] ?? x.criterio}</span>
                          <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-navy/10">
                            <span className="block h-full rounded-full bg-orange" style={{ width: `${(x.pontos / x.maximo) * 100}%` }} />
                          </span>
                          <span className="w-10 shrink-0 text-right tabular-nums">{x.pontos.toFixed(x.pontos % 1 ? 1 : 0)}/{x.maximo}</span>
                          <span className="hidden w-44 shrink-0 truncate text-mute sm:block">{x.nota}</span>
                        </li>
                      ))}
                    </motion.ul>
                  )}
                </AnimatePresence>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      )}
    </motion.article>
  );
}
