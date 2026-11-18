"use client";

import { AnimatePresence, m as motion } from "framer-motion";
import { useEffect, useReducer, useState, type ReactNode } from "react";
import { Logo } from "@/components/Logo";
import {
  Avatar, Button, Card, ChipGroup, Field, Notice, ScoreRing, Segmented, Select, Sheet, Stat, StatusBadge, spring,
} from "@/components/ui";
import { ROTULO_MODELO, type ModeloTrabalho, type StatusMatch } from "@/lib/types";
import {
  EMPRESA, EXEMPLO_CANDIDATO, EXEMPLO_VAGA, GRUPOS, ONG, PASSOS, ROTULO_ESC, SKILLS_SUGERIDAS,
  estadoInicial, sugerir, type Cand, type Estado, type MatchDemo, type Papel, type VagaDemo,
} from "./dados";
import { ArrowRight, Check, Inbox, Lightbulb, Lock, MapPin, Sparkles } from "lucide-react";

/* ------------------------------------------------------------------ estado (tudo em memória) */
type Acao =
  | { t: "reiniciar" } | { t: "consulta" } | { t: "verificar"; ok: boolean } | { t: "impacto" }
  | { t: "cand"; c: Omit<Cand, "id"> } | { t: "vaga"; v: Omit<VagaDemo, "id"> }
  | { t: "buscar"; vagaId: string } | { t: "status"; matchId: string; status: StatusMatch };

function reduzir(e: Estado, a: Acao): Estado {
  switch (a.t) {
    case "reiniciar": return estadoInicial();
    case "consulta": return { ...e, consultaFeita: true };
    case "impacto": return e.visitouImpacto ? e : { ...e, visitouImpacto: true };
    case "verificar": return { ...e, ongVerificada: a.ok };
    case "cand": return { ...e, candidatos: [{ ...a.c, id: `c${e.candidatos.length + 1}` }, ...e.candidatos] };
    case "vaga": return { ...e, vagas: [{ ...a.v, id: `v${e.vagas.length + 1}` }, ...e.vagas] };
    case "buscar": {
      const vaga = e.vagas.find((v) => v.id === a.vagaId);
      return vaga ? { ...e, matches: [...e.matches, ...sugerir(e, vaga)] } : e;
    }
    case "status": return { ...e, matches: e.matches.map((m) => (m.id === a.matchId ? { ...m, status: a.status } : m)) };
  }
}

const ABAS: Record<Papel, { value: string; label: string }[]> = {
  publico: [{ value: "cadastro", label: "Cadastro" }],
  admin: [{ value: "ongs", label: "ONGs" }],
  ong: [{ value: "candidatos", label: "Candidatos" }, { value: "funil", label: "Funil" }, { value: "impacto", label: "Impacto" }],
  empresa: [{ value: "vagas", label: "Vagas" }, { value: "impacto", label: "Impacto" }],
};
const PAPEIS: { value: Papel; label: string }[] = [
  { value: "publico", label: "Visitante" }, { value: "admin", label: "Administração" },
  { value: "ong", label: "ONG" }, { value: "empresa", label: "Empresa" },
];

const nomeCand = (e: Estado, id: string) => e.candidatos.find((c) => c.id === id);
const nomeVaga = (e: Estado, id: string) => e.vagas.find((v) => v.id === id)?.titulo ?? "";

/* ------------------------------------------------------------------ casca */
export function DemoApp() {
  const [e, dispatch] = useReducer(reduzir, undefined, estadoInicial);
  const [papel, setPapel] = useState<Papel>("publico");
  const [aba, setAba] = useState("cadastro");

  const ir = (p: Papel, a: string) => { setPapel(p); setAba(a); };
  const atual = PASSOS.find((p) => !p.feito(e));
  const feitos = PASSOS.filter((p) => p.feito(e)).length;

  return (
    <div className="mx-auto max-w-6xl px-4 pb-24 sm:px-6">
      <header className="z-40 mt-3 flex lg:sticky lg:top-3 flex-wrap items-center justify-between gap-3 rounded-[28px] glass px-4 py-2.5">
        <div className="flex items-center gap-3">
          <span className="whitespace-nowrap"><Logo /></span>
          <span className="rounded-full bg-orange/20 px-3 py-1 text-xs font-bold text-navy">Protótipo<span className="hidden sm:inline"> · dados fictícios</span></span>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Segmented label="Quem está usando" options={PAPEIS} value={papel}
            onChange={(p) => ir(p, ABAS[p][0].value)} />
          <Button size="sm" variant="ghost" onClick={() => { dispatch({ t: "reiniciar" }); ir("publico", "cadastro"); }}>Reiniciar</Button>
        </div>
      </header>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_320px]">
        <main id="conteudo" tabIndex={-1} className="min-w-0 outline-none lg:order-1">
          {atual && (
            <div className="mb-5 flex items-center gap-3 rounded-2xl bg-sun/40 px-4 py-3 lg:hidden">
              <span aria-hidden className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-navy text-xs font-bold text-cream">{atual.n}</span>
              <p className="min-w-0 flex-1 text-sm"><b>{atual.titulo}</b><span className="block text-xs text-mute">Passo {atual.n} de {PASSOS.length}</span></p>
              <Button size="sm" onClick={() => ir(atual.papel, atual.aba)}>Ir</Button>
            </div>
          )}
          <div className="mb-6">
            <Segmented label="Seção" options={ABAS[papel]} value={aba} onChange={setAba} />
          </div>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div key={`${papel}-${aba}`} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
              transition={{ duration: 0.25 }}>
              {papel === "publico" && <Cadastro e={e} dispatch={dispatch} />}
              {papel === "admin" && <Admin e={e} dispatch={dispatch} />}
              {papel === "ong" && aba === "candidatos" && <OngCandidatos e={e} dispatch={dispatch} />}
              {papel === "ong" && aba === "funil" && <OngFunil e={e} dispatch={dispatch} />}
              {papel === "ong" && aba === "impacto" && <Impacto e={e} dispatch={dispatch} tipo="ong" />}
              {papel === "empresa" && aba === "vagas" && <EmpresaVagas e={e} dispatch={dispatch} />}
              {papel === "empresa" && aba === "impacto" && <Impacto e={e} dispatch={dispatch} tipo="empresa" />}
            </motion.div>
          </AnimatePresence>
        </main>

        <aside aria-label="Roteiro da demonstração" className="lg:order-2 lg:sticky lg:top-24 lg:self-start">
          <Card className="p-5">
            <div className="flex items-baseline justify-between">
              <h2 className="title text-2xl">Roteiro</h2>
              <span className="text-xs font-semibold text-mute">{feitos} de {PASSOS.length}</span>
            </div>
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-navy/10" role="progressbar" aria-valuemin={0}
              aria-valuemax={PASSOS.length} aria-valuenow={feitos} aria-label="Progresso do roteiro">
              <motion.div className="h-full rounded-full bg-orange" animate={{ width: `${(feitos / PASSOS.length) * 100}%` }} transition={spring} />
            </div>
            <ol className="mt-4 space-y-1.5">
              {PASSOS.map((p) => {
                const feito = p.feito(e); const ativo = atual?.n === p.n;
                return (
                  <li key={p.n}>
                    <button type="button" onClick={() => ir(p.papel, p.aba)} aria-current={ativo ? "step" : undefined}
                      className={`w-full rounded-2xl px-3 py-2 text-left transition-colors ${ativo ? "bg-sun/40" : "hover:bg-navy/[.05]"}`}>
                      <span className="flex items-center gap-2.5">
                        <span aria-hidden className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${feito ? "bg-navy text-cream" : "bg-navy/10 text-navy"}`}>
                          {feito ? <Check className="h-3.5 w-3.5" strokeWidth={3} aria-hidden /> : p.n}
                        </span>
                        <span className="text-sm font-semibold">{p.titulo}</span>
                      </span>
                      {ativo && <span className="mt-1.5 block pl-[34px] text-[13px] leading-snug text-mute">{p.texto}</span>}
                    </button>
                  </li>
                );
              })}
            </ol>
            {atual ? (
              <Button className="mt-4 w-full" onClick={() => ir(atual.papel, atual.aba)}>
                Ir para o passo {atual.n} <ArrowRight className="h-4 w-4" aria-hidden />
              </Button>
            ) : (
              <Notice kind="ok">Fluxo completo: da ONG que cadastra à empresa que contrata, com privacidade e impacto medido.</Notice>
            )}
          </Card>
        </aside>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ peças comuns */
type P = { e: Estado; dispatch: (a: Acao) => void };

function Titulo({ children, sub, acao }: { children: ReactNode; sub?: string; acao?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="title text-4xl sm:text-5xl">{children}</h1>
        {sub && <p className="mt-2 max-w-xl text-[15px] text-mute">{sub}</p>}
      </div>
      {acao}
    </div>
  );
}
function Vazio({ children }: { children: ReactNode }) {
  return <Card className="flex flex-col items-center px-6 py-12 text-center"><Inbox aria-hidden className="mb-3 h-8 w-8 text-mute" /><p className="max-w-sm text-mute">{children}</p></Card>;
}
function Dica({ children }: { children: ReactNode }) {
  return <p className="mt-3 rounded-2xl bg-navy/[.04] px-4 py-3 text-[13px] leading-snug text-mute"><Lightbulb aria-hidden className="mr-2 inline h-4 w-4 align-[-3px] text-orange" />{children}</p>;
}
const campo = "w-full rounded-2xl bg-white/80 px-4 py-2.5 text-sm shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)] outline-none focus:shadow-[inset_0_0_0_1.5px_#e78b48]";

/* ------------------------------------------------------------------ 1. cadastro */
function Cadastro({ e, dispatch }: P) {
  const [tipo, setTipo] = useState<"ong" | "empresa">("ong");
  const [consultando, setConsultando] = useState(false);
  const [criada, setCriada] = useState(false);
  const d = tipo === "ong" ? ONG : EMPRESA;

  function consultar() {
    setConsultando(true);
    setTimeout(() => { setConsultando(false); dispatch({ t: "consulta" }); }, 800);
  }
  return (
    <section>
      <Titulo sub="Só o CNPJ: a razão social, o CNAE, o porte e o endereço vêm da Receita. Nada de formulário longo.">Criar conta</Titulo>
      <Card className="max-w-xl p-6 sm:p-8">
        <Segmented label="Tipo de conta" value={tipo} onChange={(t) => { setTipo(t); setCriada(false); }}
          options={[{ value: "ong", label: "Sou ONG" }, { value: "empresa", label: "Sou empresa" }]} />
        <div className="mt-5 space-y-4">
          <Field label="CNPJ" value={d.cnpj} readOnly hint="Valida os dígitos verificadores antes de consultar." />
          <Button variant="dark" onClick={consultar} loading={consultando}>Consultar na Receita</Button>
          <AnimatePresence>
            {e.consultaFeita && (
              <motion.div initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} className="space-y-3">
                <div className="rounded-2xl bg-sun/30 p-4 text-sm">
                  <p className="font-semibold">{d.razao}</p>
                  <p className="text-mute">{d.cnae}</p>
                  <p className="mt-1 text-mute">{d.cidade}{"porte" in d ? ` · Porte ${d.porte}` : ""} · <b className="text-navy">Situação ATIVA</b></p>
                </div>
                <Field label="E-mail" value="contato@exemplo.org" readOnly />
                <Field label="Senha" type="password" value="••••••••••••" readOnly hint="Mínimo de 12 caracteres; checada contra senhas vazadas." />
                <Button onClick={() => setCriada(true)}>Criar conta</Button>
                {criada && (
                  <Notice kind="ok">Conta criada. Enviamos um link de verificação de e-mail (24 h, uso único).
                    {tipo === "ong" ? " A ONG fica pendente até a administração verificar." : ""}</Notice>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </Card>
      <Dica>No sistema real, CNPJ inativo ou inexistente é recusado e há limite de tentativas contra abuso.</Dica>
    </section>
  );
}

/* ------------------------------------------------------------------ 2. administração */
function Admin({ e, dispatch }: P) {
  const contratados = e.matches.filter((m) => m.status === "CONTRATADO").length;
  return (
    <section>
      <Titulo sub="Verifique as ONGs que podem enviar candidatos e acompanhe a plataforma.">Administração</Titulo>
      <div className="mb-8 grid grid-cols-2 gap-3 md:grid-cols-3">
        <Stat label="ONGs pendentes" value={e.ongVerificada ? 0 : 1} accent={!e.ongVerificada} />
        <Stat label="ONGs verificadas" value={e.ongVerificada ? 1 : 0} />
        <Stat label="Empresas" value={1} />
        <Stat label="Candidatos" value={e.candidatos.length} />
        <Stat label="Vagas abertas" value={e.vagas.length} />
        <Stat label="Contratados" value={contratados} />
      </div>
      <h2 className="title mb-3 text-2xl">ONGs</h2>
      <Card className="flex flex-wrap items-center gap-4 p-4 sm:p-5">
        <Avatar name={ONG.razao} size={48} />
        <div className="min-w-0 flex-1 basis-48">
          <p className="truncate text-lg font-semibold">{ONG.razao}</p>
          <p className="truncate text-sm text-mute">{ONG.cnpj} · {ONG.cidade}</p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${e.ongVerificada ? "bg-navy text-cream" : "bg-sun/50 text-navy"}`}>
          {e.ongVerificada ? "Verificada" : "Pendente"}
        </span>
        <Button variant={e.ongVerificada ? "danger" : "primary"} size="sm" onClick={() => dispatch({ t: "verificar", ok: !e.ongVerificada })}>
          {e.ongVerificada ? "Revogar" : "Verificar ONG"}
        </Button>
      </Card>
      <Dica>A verificação fica registrada na trilha de auditoria, que nunca guarda nome nem e-mail de pessoas.</Dica>
    </section>
  );
}

/* ------------------------------------------------------------------ 3. ONG: candidatos */
function OngCandidatos({ e, dispatch }: P) {
  const [aberto, setAberto] = useState(false);
  return (
    <section>
      <Titulo sub="Quem a ONG atende. Os dados sensíveis ficam só com a ONG." acao={<Button onClick={() => setAberto(true)}>Cadastrar candidato</Button>}>
        Candidatos ({e.candidatos.length})
      </Titulo>
      {!e.ongVerificada && <div className="mb-4"><Notice kind="info">Sua ONG ainda não foi verificada: pode cadastrar pessoas, mas só entra nos matches depois da verificação (passo 2).</Notice></div>}
      <ul className="space-y-3">
        {e.candidatos.map((c) => (
          <motion.li key={c.id} layout initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
            className="glass flex flex-wrap items-center gap-4 rounded-[26px] p-4 sm:p-5">
            <Avatar name={c.nome} size={46} />
            <div className="min-w-0 flex-1 basis-48">
              <p className="truncate text-lg font-semibold tracking-tight">{c.nome}</p>
              <p className="truncate text-sm text-mute">{c.idade} anos · {c.bairro} · {ROTULO_ESC[c.escolaridade]} · {ROTULO_MODELO[c.modelo]}</p>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {c.skills.map((s) => <span key={s} className="rounded-full bg-sun/50 px-2.5 py-1 text-xs font-semibold">{s}</span>)}
              {c.grupos.map((g) => <span key={g} title="Autodeclaração: só a ONG vê" className="inline-flex items-center gap-1 rounded-full bg-navy/[.07] px-2.5 py-1 text-xs font-semibold text-mute"><Lock aria-hidden className="h-3 w-3" /> {g}</span>)}
            </div>
          </motion.li>
        ))}
      </ul>
      <Dica>O cadeado <Lock aria-hidden className="inline h-3.5 w-3.5 align-[-2px]" /> marca autodeclarações opcionais (gênero, raça/cor, PCD…): só a ONG as vê; a empresa nunca recebe esses dados.</Dica>
      <CandSheet aberto={aberto} onClose={() => setAberto(false)} onSalvar={(c) => { dispatch({ t: "cand", c }); setAberto(false); }} />
    </section>
  );
}

function CandSheet({ aberto, onClose, onSalvar }: { aberto: boolean; onClose: () => void; onSalvar: (c: Omit<Cand, "id">) => void }) {
  const vazio: Omit<Cand, "id"> = { nome: "", idade: 30, bairro: "", km: 4, modelo: "PRESENCIAL", escolaridade: "MEDIO", skills: [], regime: "CLT", grupos: [] };
  const [f, setF] = useState(vazio);
  useEffect(() => { if (aberto) setF(vazio); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [aberto]);
  return (
    <Sheet open={aberto} onClose={onClose} title="Cadastrar candidato">
      <form className="space-y-4" onSubmit={(ev) => { ev.preventDefault(); if (f.nome.trim()) onSalvar(f); }}>
        <Button type="button" variant="ghost" size="sm" onClick={() => setF({ ...EXEMPLO_CANDIDATO })}><Sparkles className="h-4 w-4" aria-hidden /> Preencher exemplo</Button>
        <Field label="Nome completo" value={f.nome} onChange={(x) => setF({ ...f, nome: x.target.value })} required />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Idade (maior de 18)" type="number" min={18} value={f.idade} onChange={(x) => setF({ ...f, idade: Number(x.target.value) })} />
          <Field label="Bairro" value={f.bairro} onChange={(x) => setF({ ...f, bairro: x.target.value })} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Escolaridade" value={f.escolaridade} onChange={(x) => setF({ ...f, escolaridade: x.target.value as Cand["escolaridade"] })}>
            {Object.entries(ROTULO_ESC).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </Select>
          <Select label="Modelo preferido" value={f.modelo} onChange={(x) => setF({ ...f, modelo: x.target.value as ModeloTrabalho })}>
            {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </Select>
        </div>
        <ChipGroup label="Habilidades" options={SKILLS_SUGERIDAS.map((s) => ({ codigo: s, rotulo: s }))} value={f.skills} onChange={(skills) => setF({ ...f, skills })} />
        <ChipGroup label="Autodeclaração (opcional, só a ONG vê)" options={GRUPOS.map((g) => ({ codigo: g, rotulo: g }))} value={f.grupos} onChange={(grupos) => setF({ ...f, grupos })} />
        <Button type="submit" className="w-full !py-3.5">Salvar candidato</Button>
      </form>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ 6. ONG: funil */
function OngFunil({ e, dispatch }: P) {
  const [aberto, setAberto] = useState<string>();
  return (
    <section>
      <Titulo sub="Sugestões do motor de match. A ONG vê a nota completa e decide quem enviar.">Funil</Titulo>
      {e.matches.length === 0 ? (
        <Vazio>Ainda não há sugestões. Elas aparecem quando uma empresa publica uma vaga e busca candidatos (passos 4 e 5).</Vazio>
      ) : (
        <ul className="space-y-3">
          {e.matches.map((m) => {
            const c = nomeCand(e, m.candId); if (!c) return null;
            return (
              <li key={m.id} className="glass rounded-[26px] p-4 sm:p-5">
                <div className="flex flex-wrap items-center gap-4">
                  <ScoreRing score={m.score} size={52} />
                  <div className="min-w-0 flex-1 basis-40">
                    <p className="truncate text-lg font-semibold tracking-tight">{c.nome}</p>
                    <p className="truncate text-sm text-mute">para {nomeVaga(e, m.vagaId)} · #{m.posicao} no ranking</p>
                  </div>
                  <StatusBadge status={m.status} />
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="ghost" onClick={() => setAberto(aberto === m.id ? undefined : m.id)} aria-expanded={aberto === m.id}>Por quê?</Button>
                    {m.status === "SUGERIDO" && <Button size="sm" onClick={() => dispatch({ t: "status", matchId: m.id, status: "ENVIADO" })}>Enviar à empresa</Button>}
                  </div>
                </div>
                <AnimatePresence initial={false}>{aberto === m.id && <Motivos m={m} comPrioridade />}</AnimatePresence>
              </li>
            );
          })}
        </ul>
      )}
      <Dica>A ONG é a única a ver a nota e o critério “Vaga afirmativa”, porque conhece as autodeclarações dos seus assistidos.</Dica>
    </section>
  );
}

function Motivos({ m, comPrioridade }: { m: MatchDemo; comPrioridade: boolean }) {
  const itens = m.motivos.filter((x) => comPrioridade || x.criterio !== "Vaga afirmativa");
  return (
    <motion.ul initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
      className="mt-4 space-y-2.5 overflow-hidden border-t border-navy/10 pt-4">
      {itens.map((x) => (
        <li key={x.criterio} className="text-sm">
          <div className="flex items-baseline justify-between gap-3">
            <span className="font-medium">{x.criterio}</span>
            <span className="tabular-nums text-mute">{x.pontos}/{x.maximo}</span>
          </div>
          <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-navy/10">
            <motion.div className="h-full rounded-full bg-orange" initial={{ width: 0 }} animate={{ width: `${(x.pontos / x.maximo) * 100}%` }} transition={{ duration: 0.7 }} />
          </div>
          <p className="mt-0.5 text-xs text-mute">{x.nota}</p>
        </li>
      ))}
    </motion.ul>
  );
}

/* ------------------------------------------------------------------ 4-5, 7. empresa: vagas */
function EmpresaVagas({ e, dispatch }: P) {
  const [aberto, setAberto] = useState(false);
  return (
    <section>
      <Titulo sub="Publique a vaga e receba candidatos pré-qualificados pelas ONGs parceiras." acao={<Button onClick={() => setAberto(true)}>Publicar vaga</Button>}>Vagas</Titulo>
      {e.vagas.length === 0 ? <Vazio>Nenhuma vaga ainda. Publique a primeira (use “Preencher exemplo” para ir rápido).</Vazio> : (
        <ul className="space-y-6">{e.vagas.map((v) => <VagaCard key={v.id} e={e} dispatch={dispatch} vaga={v} />)}</ul>
      )}
      <VagaSheet aberto={aberto} onClose={() => setAberto(false)} onSalvar={(v) => { dispatch({ t: "vaga", v }); setAberto(false); }} />
    </section>
  );
}

function VagaCard({ e, dispatch, vaga }: P & { vaga: VagaDemo }) {
  const [aberto, setAberto] = useState<string>();
  const ms = e.matches.filter((m) => m.vagaId === vaga.id).sort((a, b) => a.posicao - b.posicao);
  const set = (m: MatchDemo, status: StatusMatch) => dispatch({ t: "status", matchId: m.id, status });
  return (
    <li className="glass rounded-[28px] p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="title text-2xl">{vaga.titulo}</h2>
          <p className="mt-1 text-sm text-mute">{ROTULO_MODELO[vaga.modelo]} · CEP {vaga.cep} · {vaga.escolaridadeMin ? `mín. ${ROTULO_ESC[vaga.escolaridadeMin]}` : "sem exigência de escolaridade"}</p>
          <div className="mt-2 flex flex-wrap gap-1.5">
            {vaga.skills.map((s) => <span key={s} className="rounded-full bg-sun/50 px-2.5 py-1 text-xs font-semibold">{s}</span>)}
            {vaga.afirmativa && <span className="rounded-full bg-navy px-2.5 py-1 text-xs font-semibold text-cream">Vaga afirmativa</span>}
          </div>
        </div>
        <Button onClick={() => dispatch({ t: "buscar", vagaId: vaga.id })} disabled={ms.length > 0}>Buscar candidatos</Button>
      </div>
      {!e.ongVerificada && <div className="mt-4"><Notice kind="info">Nenhuma ONG verificada ainda: a busca não encontra ninguém. Verifique a ONG como administração (passo 2).</Notice></div>}
      {ms.length > 0 && (
        <ol className="mt-5 space-y-2.5" aria-label="Ranking de candidatos">
          {ms.map((m) => {
            const c = nomeCand(e, m.candId); if (!c) return null;
            return (
              <li key={m.id} className="rounded-[22px] bg-white/60 p-3.5 sm:p-4">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="title w-9 text-2xl text-navy" aria-label={`Posição ${m.posicao}`}>#{m.posicao}</span>
                  <Avatar name={c.nome} size={40} />
                  <div className="min-w-0 flex-1 basis-36">
                    <p className="truncate font-semibold">{c.nome}</p>
                    <p className="truncate text-xs text-mute">{c.idade} anos · {c.bairro}</p>
                  </div>
                  <StatusBadge status={m.status} />
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="ghost" aria-expanded={aberto === m.id} onClick={() => setAberto(aberto === m.id ? undefined : m.id)}>Por quê?</Button>
                    {m.status === "ENVIADO" && <Button size="sm" onClick={() => set(m, "ENTREVISTA")}>Chamar para entrevista</Button>}
                    {m.status === "ENTREVISTA" && <Button size="sm" variant="dark" onClick={() => set(m, "CONTRATADO")}>Contratar</Button>}
                    {(m.status === "ENVIADO" || m.status === "ENTREVISTA") && <Button size="sm" variant="danger" onClick={() => set(m, "RECUSADO")}>Recusar</Button>}
                  </div>
                </div>
                {m.status === "SUGERIDO" && <p className="mt-2 pl-1 text-xs text-mute">Aguardando a ONG enviar este candidato (passo 6).</p>}
                <AnimatePresence initial={false}>{aberto === m.id && <Motivos m={m} comPrioridade={false} />}</AnimatePresence>
              </li>
            );
          })}
        </ol>
      )}
      {ms.length > 0 && <Dica>A empresa vê só a posição no ranking e os motivos, sem a nota e sem o critério de vaga afirmativa: ninguém consegue deduzir o grupo de um candidato.</Dica>}
    </li>
  );
}

function VagaSheet({ aberto, onClose, onSalvar }: { aberto: boolean; onClose: () => void; onSalvar: (v: Omit<VagaDemo, "id">) => void }) {
  const vazio: Omit<VagaDemo, "id"> = { titulo: "", modelo: "PRESENCIAL", cep: "", escolaridadeMin: "", skills: [], afirmativa: false, grupos: [], justificativa: "" };
  const [f, setF] = useState(vazio);
  useEffect(() => { if (aberto) setF(vazio); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [aberto]);
  const justOk = !f.afirmativa || f.justificativa.trim().length >= 20;
  return (
    <Sheet open={aberto} onClose={onClose} title="Publicar vaga">
      <form className="space-y-4" onSubmit={(ev) => { ev.preventDefault(); if (f.titulo.trim() && justOk) onSalvar(f); }}>
        <Button type="button" variant="ghost" size="sm" onClick={() => setF({ ...EXEMPLO_VAGA })}><Sparkles className="h-4 w-4" aria-hidden /> Preencher exemplo</Button>
        <Field label="Cargo" value={f.titulo} onChange={(x) => setF({ ...f, titulo: x.target.value })} required />
        <div className="grid gap-4 sm:grid-cols-2">
          <Select label="Modelo de trabalho" value={f.modelo} onChange={(x) => setF({ ...f, modelo: x.target.value as ModeloTrabalho })}>
            {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </Select>
          <Field label="CEP de atuação" value={f.cep} placeholder="00000-000" onChange={(x) => setF({ ...f, cep: x.target.value })} />
        </div>
        {f.cep.replace(/\D/g, "").length === 8 && <p className="rounded-2xl bg-sun/30 px-4 py-3 text-sm"><MapPin aria-hidden className="mr-1.5 inline h-4 w-4 align-[-3px]" />Campinas/SP · localização obtida pelo CEP</p>}
        <Select label="Escolaridade mínima" value={f.escolaridadeMin} onChange={(x) => setF({ ...f, escolaridadeMin: x.target.value as VagaDemo["escolaridadeMin"] })}>
          <option value="">Sem exigência</option>
          {Object.entries(ROTULO_ESC).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </Select>
        <ChipGroup label="Habilidades desejadas" options={SKILLS_SUGERIDAS.map((s) => ({ codigo: s, rotulo: s }))} value={f.skills} onChange={(skills) => setF({ ...f, skills })} />
        <div className="rounded-2xl bg-navy/[.04] p-4">
          <label className="flex cursor-pointer items-start gap-3 text-sm">
            <input type="checkbox" checked={f.afirmativa} onChange={(x) => setF({ ...f, afirmativa: x.target.checked })} className="mt-0.5 h-5 w-5 shrink-0 accent-[#e78b48]" />
            <span><b>Vaga afirmativa</b> (opcional)<span className="block text-mute">Grupos escolhidos ganham pontos extras. Ninguém é excluído.</span></span>
          </label>
          {f.afirmativa && (
            <div className="mt-4 space-y-4">
              <ChipGroup label="Grupos priorizados" options={GRUPOS.map((g) => ({ codigo: g, rotulo: g }))} value={f.grupos} onChange={(grupos) => setF({ ...f, grupos })} />
              <label className="block text-[13px] font-medium text-mute">Justificativa (mín. 20 caracteres)
                <textarea rows={3} maxLength={500} value={f.justificativa} onChange={(x) => setF({ ...f, justificativa: x.target.value })} className={`${campo} mt-1.5`} />
              </label>
            </div>
          )}
        </div>
        <Button type="submit" className="w-full !py-3.5" disabled={!f.titulo.trim() || !justOk}>Publicar vaga</Button>
      </form>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ 8. impacto */
function Impacto({ e, dispatch, tipo }: P & { tipo: "ong" | "empresa" }) {
  useEffect(() => { dispatch({ t: "impacto" }); }, [dispatch]);
  const q = (s: StatusMatch) => e.matches.filter((m) => m.status === s).length;
  const contr = q("CONTRATADO"); const enviados = e.matches.filter((m) => m.status !== "SUGERIDO").length;
  const taxa = enviados ? Math.round((contr / enviados) * 100) : null;
  const tiles: [string, string | number, boolean?][] = tipo === "ong" ? [
    ["Candidatos ativos", e.candidatos.length], ["Aguardando envio", q("SUGERIDO")], ["Em análise", q("ENVIADO")],
    ["Em entrevista", q("ENTREVISTA")], ["Contratações", contr, true], ["Taxa de colocação", taxa == null ? "—" : `${taxa}%`],
    ["Tempo médio até contratar", contr ? "6 dias" : "—"], ["Empresas parceiras", contr ? 1 : 0],
  ] : [
    ["Vagas abertas", e.vagas.length], ["Sugestões recebidas", e.matches.length], ["Candidatos enviados", enviados],
    ["Entrevistas", q("ENTREVISTA") + contr], ["Contratações", contr, true], ["ONGs parceiras", e.matches.length ? 1 : 0],
    ["Tempo médio até contratar", contr ? "6 dias" : "—"], ["Contratações em vagas afirmativas", contr && e.vagas.some((v) => v.afirmativa) ? contr : 0],
  ];
  return (
    <section>
      <Titulo sub="Relatório pronto para prestação de contas. Só totais, sem recortes por gênero, raça/cor ou deficiência."
        acao={<Button size="sm" variant="ghost" onClick={() => window.print()} className="print:hidden">Imprimir / salvar PDF</Button>}>
        Impacto {tipo === "ong" ? "da ONG" : "da empresa"}
      </Titulo>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {tiles.map(([r, v, d]) => (
          <div key={r} className={`rounded-[22px] p-4 ${d ? "bg-sun/50" : "bg-white/70"}`}>
            <div className="title text-3xl tabular-nums">{v}</div>
            <div className="mt-1 text-xs font-medium text-mute">{r}</div>
          </div>
        ))}
      </div>
      <Dica>Os números acima se atualizam conforme você percorre o fluxo: volte aqui depois de contratar alguém.</Dica>
    </section>
  );
}
