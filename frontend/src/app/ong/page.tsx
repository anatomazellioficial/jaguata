"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { CepField } from "@/components/Lookups";
import { Shell } from "@/components/Shell";
import { Avatar, Button, Card, ChipGroup, Field, Notice, ScoreRing, Segmented, Select, Sheet, Skeleton, StatusBadge, spring } from "@/components/ui";
import { useCatalogo } from "@/lib/catalogo";
import Link from "next/link";
import { ApiError, api, baixar, getSession } from "@/lib/api";
import {
  ROTULO_MODELO, ROTULO_STATUS,
  type CandidatoResumo, type CategoriaTag, type Match, type ModeloTrabalho, type Pagina, type StatusMatch,
} from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");
const ABAS = (Object.keys(ROTULO_STATUS) as StatusMatch[]).map((s) => ({ value: s, label: ROTULO_STATUS[s] }));

function maxNascimento() {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 18);
  return d.toISOString().slice(0, 10);
}

export default function OngPage() {
  const [ongId, setOngId] = useState<string>();
  const [erro, setErro] = useState<string>();
  const [sheet, setSheet] = useState(false);
  const [cadastrados, setCadastrados] = useState(0);
  const [secao, setSecao] = useState<"funil" | "candidatos" | "areas">("funil");

  useEffect(() => {
    getSession()
      .then((s) => (s.ongId ? setOngId(s.ongId) : setErro("Esta conta não está vinculada a uma ONG.")))
      .catch((e) => setErro(msg(e)));
  }, []);

  return (
    <Shell
      title="Sua ONG"
      subtitle="Cadastre seus assistidos (18+) e envie os melhores perfis para as vagas sugeridas."
      action={ongId ? <Button size="sm" onClick={() => setSheet(true)}>＋ Novo candidato</Button> : undefined}
      mobileAction={ongId ? { label: "Novo candidato", onClick: () => setSheet(true) } : undefined}
    >
      {erro && <Notice>{erro}</Notice>}
      {ongId && (
        <div className="mb-8">
          <Segmented label="Seção" value={secao} onChange={setSecao}
            options={[{ value: "funil", label: "Funil" }, { value: "candidatos", label: "Meus candidatos" }, { value: "areas", label: "Áreas de atuação" }]} />
        </div>
      )}
      {ongId && secao === "funil" && <Fila ongId={ongId} novoCadastro={cadastrados} />}
      {ongId && secao === "candidatos" && <ListaCandidatos ongId={ongId} atualizar={cadastrados} />}
      {ongId && secao === "areas" && <Areas ongId={ongId} />}
      {ongId && (
        <Sheet open={sheet} onClose={() => setSheet(false)} title="Novo candidato">
          <NovoCandidato ongId={ongId} onCadastrado={() => setCadastrados((n) => n + 1)} onFechar={() => setSheet(false)} />
        </Sheet>
      )}
    </Shell>
  );
}

function Fila({ ongId, novoCadastro }: { ongId: string; novoCadastro: number }) {
  const [aba, setAba] = useState<StatusMatch>("SUGERIDO");
  const [matches, setMatches] = useState<Match[]>();
  const [erro, setErro] = useState<string>();

  const carregar = useCallback(async () => {
    setMatches(await api<Match[]>(`ongs/${ongId}/matches?status=${aba}`));
  }, [ongId, aba]);

  useEffect(() => { setMatches(undefined); carregar().catch((e) => setErro(msg(e))); }, [carregar, novoCadastro]);

  async function mover(id: string, status: "ENVIADO" | "RECUSADO") {
    setErro(undefined);
    try { await api(`matches/${id}/status`, { method: "PATCH", body: { status } }); await carregar(); }
    catch (e) { setErro(msg(e)); }
  }

  return (
    <section>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h2 className="title text-3xl">Funil</h2>
        <Segmented label="Etapa do funil" value={aba} onChange={setAba} options={ABAS} />
      </div>
      {erro && <div className="mb-4"><Notice>{erro}</Notice></div>}

      {!matches && <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>}

      {matches && matches.length === 0 && (
        <Card className="flex flex-col items-center px-6 py-14 text-center">
          <motion.div animate={{ y: [0, -6, 0] }} transition={{ repeat: Infinity, duration: 3.2, ease: "easeInOut" }}
            className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-sun/50 text-2xl">📭</motion.div>
          <p className="max-w-sm text-mute">
            {aba === "SUGERIDO"
              ? "Nenhuma sugestão por enquanto. Matches aparecem quando uma empresa busca candidatos e sua ONG está verificada."
              : "Nada nesta etapa."}
          </p>
        </Card>
      )}

      <ul className="space-y-3">
        <AnimatePresence initial={false} mode="popLayout">
          {matches?.map((m, i) => (
            <motion.li key={m.id} layout initial={{ opacity: 0, y: 18, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, x: -40, scale: 0.97 }} transition={{ ...spring, delay: Math.min(i * 0.05, 0.3) }}
              whileHover={{ y: -2 }}
              className="glass flex flex-wrap items-center gap-4 rounded-[26px] p-4 sm:p-5">
              <Avatar name={m.candidatoNome} size={48} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-lg font-semibold tracking-tight">{m.candidatoNome}</p>
                <p className="truncate text-sm text-mute">para {m.tituloVaga}</p>
              </div>
              <ScoreRing score={m.matchScore} size={50} />
              <div className="flex flex-wrap items-center gap-2">
                <StatusBadge status={m.status} />
                {m.status === "SUGERIDO" && <>
                  <Button size="sm" onClick={() => mover(m.id, "ENVIADO")}>Enviar à empresa</Button>
                  <Button size="sm" variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Descartar</Button>
                </>}
                {m.status === "ENVIADO" && <Button size="sm" variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Retirar</Button>}
              </div>
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </section>
  );
}

function Areas({ ongId }: { ongId: string }) {
  const cat = useCatalogo();
  const [sel, setSel] = useState<string[]>();
  const [erro, setErro] = useState<string>();
  const [ok, setOk] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<string[]>(`ongs/${ongId}/setores`).then(setSel).catch((e) => setErro(msg(e)));
  }, [ongId]);

  async function salvar() {
    setBusy(true); setErro(undefined); setOk(false);
    try { setSel(await api<string[]>(`ongs/${ongId}/setores`, { method: "PUT", body: { setores: sel } })); setOk(true); }
    catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }

  return (
    <section className="max-w-2xl">
      <h2 className="title text-3xl">Áreas de atuação</h2>
      <p className="mt-2 text-mute">Em quais setores a sua ONG tem parceria ou tradição de encaminhar pessoas? Isso pesa no ranking
        das vagas das empresas desses setores (15 dos 100 pontos).</p>
      {sel && sel.length === 0 && <div className="mt-4"><Notice kind="info">Você ainda não escolheu áreas: todas as empresas recebem pontuação neutra neste critério.</Notice></div>}
      <div className="mt-6">
        {!sel || !cat ? <Skeleton className="h-32" /> : (
          <ChipGroup label="Setores" options={cat.setores} value={sel} onChange={(v) => { setSel(v); setOk(false); }} max={15} />
        )}
      </div>
      {erro && <div className="mt-4"><Notice>{erro}</Notice></div>}
      {ok && <div className="mt-4"><Notice kind="ok">Áreas salvas.</Notice></div>}
      <Button className="mt-6" onClick={salvar} loading={busy} disabled={!sel}>Salvar áreas</Button>
    </section>
  );
}

function ListaCandidatos({ ongId, atualizar }: { ongId: string; atualizar: number }) {
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<CandidatoResumo>>();
  const [erro, setErro] = useState<string>();
  const [confirmar, setConfirmar] = useState<string>();
  const [busy, setBusy] = useState<string>();
  const cat = useCatalogo();
  const TAM = 10;

  const carregar = useCallback(async () => {
    setDados(await api<Pagina<CandidatoResumo>>(`ongs/${ongId}/candidatos?pagina=${pagina}&tamanho=${TAM}`));
  }, [ongId, pagina]);
  useEffect(() => { carregar().catch((e) => setErro(msg(e))); }, [carregar, atualizar]);

  async function exportar(c: CandidatoResumo) {
    setErro(undefined);
    try { await baixar(`ongs/${ongId}/candidatos/${c.id}/dados`, `candidato-${c.id.slice(0, 8)}.json`); }
    catch (e) { setErro(msg(e)); }
  }

  async function anonimizar(c: CandidatoResumo) {
    setBusy(c.id); setErro(undefined); setConfirmar(undefined);
    try { await api(`ongs/${ongId}/candidatos/${c.id}`, { method: "DELETE" }); await carregar(); }
    catch (e) { setErro(msg(e)); } finally { setBusy(undefined); }
  }

  const paginas = dados ? Math.max(1, Math.ceil(dados.total / TAM)) : 1;

  return (
    <section>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <h2 className="title text-3xl">Candidatos{dados ? ` (${dados.total})` : ""}</h2>
        <p className="max-w-md text-sm text-mute">
          A pedido do(a) assistido(a), exporte os dados dele(a) ou anonimize-os (LGPD). Cada ação fica registrada na auditoria.{" "}
          <Link href="/privacidade" className="font-semibold text-brick underline">Saiba mais</Link>
        </p>
      </div>
      {erro && <div className="mb-4"><Notice>{erro}</Notice></div>}
      {!dados && <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-24" /></div>}
      {dados && dados.itens.length === 0 && (
        <Card className="px-6 py-14 text-center"><p className="text-mute">Nenhum candidato cadastrado ainda. Use “＋ Novo candidato”.</p></Card>
      )}
      <ul className="space-y-3">
        <AnimatePresence initial={false} mode="popLayout">
          {dados?.itens.map((c, i) => (
            <motion.li key={c.id} layout initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, x: -40, scale: 0.97 }} transition={{ ...spring, delay: Math.min(i * 0.04, 0.25) }}
              className="glass flex flex-wrap items-center gap-4 rounded-[26px] p-4 sm:p-5">
              <Avatar name={c.nomeCompleto} size={48} />
              <div className="min-w-[10rem] flex-1">
                <p className="truncate text-lg font-semibold tracking-tight">{c.nomeCompleto}</p>
                <p className="text-sm text-mute">{c.idade} anos · {ROTULO_MODELO[c.modeloTrabalhoPref]}
                  {c.escolaridade ? ` · ${cat?.escolaridades.find((o) => o.codigo === c.escolaridade)?.rotulo ?? c.escolaridade}` : ""}
                  {c.regimes?.length ? ` · ${c.regimes.join("/")}` : ""}</p>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {c.tags.map((t) => <span key={t.categoria + t.tag} className="rounded-full bg-sun/50 px-2.5 py-0.5 text-xs font-semibold">{t.tag}</span>)}
                </div>
              </div>
              <div className="flex w-full flex-wrap items-center justify-end gap-2 sm:w-auto">
                <Button size="sm" variant="ghost" onClick={() => exportar(c)}>Exportar dados</Button>
                {confirmar !== c.id ? (
                  <Button size="sm" variant="danger" onClick={() => setConfirmar(c.id)}>Anonimizar</Button>
                ) : (
                  <>
                    <span className="basis-full text-right text-xs text-mute sm:basis-auto sm:max-w-[14rem]">Apaga nome, nascimento e CEP. Não dá para desfazer.</span>
                    <Button size="sm" variant="danger" loading={busy === c.id} onClick={() => anonimizar(c)}>Confirmar</Button>
                    <Button size="sm" variant="ghost" onClick={() => setConfirmar(undefined)}>Cancelar</Button>
                  </>
                )}
              </div>
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
      {dados && paginas > 1 && (
        <div className="mt-6 flex items-center justify-center gap-3">
          <Button size="sm" variant="ghost" disabled={pagina === 0} onClick={() => setPagina((p) => p - 1)}>← Anterior</Button>
          <span className="text-sm text-mute">Página {pagina + 1} de {paginas}</span>
          <Button size="sm" variant="ghost" disabled={pagina + 1 >= paginas} onClick={() => setPagina((p) => p + 1)}>Próxima →</Button>
        </div>
      )}
    </section>
  );
}

function NovoCandidato({ ongId, onCadastrado, onFechar }: { ongId: string; onCadastrado: () => void; onFechar: () => void }) {
  const [f, setF] = useState({
    nomeCompleto: "", dataNascimento: "", cep: "", modeloTrabalhoPref: "PRESENCIAL" as ModeloTrabalho,
    escolaridade: "", genero: "", racaCor: "",
  });
  const cat = useCatalogo();
  const [regimes, setRegimes] = useState<string[]>([]);
  const [tags, setTags] = useState<{ categoria: CategoriaTag; tag: string }[]>([]);
  const [novaTag, setNovaTag] = useState({ categoria: "VULNERABILIDADE" as CategoriaTag, tag: "" });
  const [consentimento, setConsentimento] = useState(false);
  const [erro, setErro] = useState<string>();
  const [erros, setErros] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((s) => ({ ...s, [k]: e.target.value }));

  function addTag() {
    const t = novaTag.tag.trim();
    if (!t || tags.some((x) => x.categoria === novaTag.categoria && x.tag.toLowerCase() === t.toLowerCase())) return;
    setTags((s) => [...s, { categoria: novaTag.categoria, tag: t }]);
    setNovaTag((s) => ({ ...s, tag: "" }));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined); setErros({}); setLoading(true);
    try {
      await api(`ongs/${ongId}/candidatos`, {
        method: "POST",
        body: {
          ...f, cep: f.cep.replace(/\D/g, ""), tags, consentimentoConfirmado: consentimento,
          escolaridade: f.escolaridade || null, genero: f.genero || null, racaCor: f.racaCor || null, regimes,
        },   // coordenadas são obtidas no servidor, pelo CEP
      });
      onCadastrado();
      onFechar();
    } catch (err) {
      setErro(msg(err));
      if (err instanceof ApiError) setErros(err.campos ?? {});
      setLoading(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Nome completo" required value={f.nomeCompleto} onChange={set("nomeCompleto")} error={erros.nomeCompleto} />
      <Field label="Data de nascimento" type="date" required max={maxNascimento()} value={f.dataNascimento}
        onChange={set("dataNascimento")} hint="Apenas maiores de 18 anos." error={erros.dataNascimento} />
      <CepField label="CEP do candidato" value={f.cep} error={erros.cep}
        onChange={(v) => setF((s) => ({ ...s, cep: v }))} />
      <Select label="Preferência de trabalho" value={f.modeloTrabalhoPref} onChange={set("modeloTrabalhoPref")}>
        {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </Select>

      <Select label="Escolaridade" value={f.escolaridade} onChange={set("escolaridade")}>
        <option value="">Não informada</option>
        {cat?.escolaridades.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
      </Select>
      <ChipGroup label="Regimes de contratação aceitos" options={cat?.regimes ?? []} value={regimes} onChange={setRegimes} />

      <details className="rounded-2xl bg-navy/[.04] p-4 text-sm">
        <summary className="cursor-pointer font-semibold">Autodeclaração (opcional)</summary>
        <p className="mt-2 text-mute">Só preencha se o(a) assistido(a) quiser. Usada apenas para dar prioridade em vagas afirmativas;
          nunca é mostrada às empresas e é apagada na anonimização.</p>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <Select label="Gênero" value={f.genero} onChange={set("genero")}>
            <option value="">Prefere não informar</option>
            {cat?.generos.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
          </Select>
          <Select label="Raça/cor" value={f.racaCor} onChange={set("racaCor")}>
            <option value="">Prefere não informar</option>
            {cat?.racas.map((o) => <option key={o.codigo} value={o.codigo}>{o.rotulo}</option>)}
          </Select>
        </div>
      </details>

      <fieldset className="space-y-3">
        <legend className="mb-1.5 text-[13px] font-medium text-mute">Tags</legend>
        <div className="flex gap-2">
          <select aria-label="Categoria da tag" value={novaTag.categoria}
            onChange={(e) => setNovaTag((s) => ({ ...s, categoria: e.target.value as CategoriaTag }))}
            className="rounded-2xl bg-white/80 px-3 text-xs font-medium text-navy shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)]">
            <option value="VULNERABILIDADE">Vulnerabilidade</option>
            <option value="DEMOGRAFIA">Demografia</option>
            <option value="SKILL">Skill</option>
          </select>
          <input aria-label="Tag" placeholder="PCD, Egresso, 50+…" value={novaTag.tag}
            onChange={(e) => setNovaTag((s) => ({ ...s, tag: e.target.value }))}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
            className="min-w-0 flex-1 rounded-2xl bg-white/80 px-4 py-2.5 text-sm shadow-[inset_0_0_0_1px_rgba(16,46,80,.10)] outline-none focus:shadow-[inset_0_0_0_1.5px_#e78b48]" />
          <Button type="button" variant="ghost" onClick={addTag}>＋</Button>
        </div>
        <div className="flex flex-wrap gap-2">
          <AnimatePresence>
            {tags.map((t) => (
              <motion.button key={t.categoria + t.tag} type="button" layout
                initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.6 }} transition={spring}
                onClick={() => setTags((s) => s.filter((x) => x !== t))} title="Remover"
                className="rounded-full bg-sun/50 px-3 py-1 text-xs font-semibold hover:bg-brick/10 hover:text-brick">
                {t.tag} ✕
              </motion.button>
            ))}
          </AnimatePresence>
        </div>
      </fieldset>

      <label className="flex cursor-pointer items-start gap-3 rounded-2xl bg-navy/[.04] p-4 text-sm">
        <input type="checkbox" checked={consentimento} onChange={(e) => setConsentimento(e.target.checked)}
          className="mt-0.5 h-5 w-5 shrink-0 accent-[#e78b48]" />
        <span>
          Declaro que o(a) assistido(a) leu e assinou o{" "}
          <Link href="/termos#consentimento" target="_blank" className="font-semibold text-brick underline">termo de consentimento</Link>{" "}
          para o tratamento dos seus dados. Esta confirmação fica registrada com a data e o meu usuário.
        </span>
      </label>
      {erros.consentimentoConfirmado && <Notice>{erros.consentimentoConfirmado}</Notice>}
      {erro && <Notice>{erro}</Notice>}
      <Button type="submit" loading={loading} disabled={!consentimento} className="w-full !py-3.5">Cadastrar candidato</Button>
    </form>
  );
}
