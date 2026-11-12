"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { CepField } from "@/components/Lookups";
import { Shell } from "@/components/Shell";
import { Avatar, Button, Card, Field, Notice, ScoreRing, Segmented, Select, Sheet, Skeleton, StatusBadge, spring } from "@/components/ui";
import { ApiError, api, getSession } from "@/lib/api";
import { ROTULO_MODELO, ROTULO_STATUS, type CategoriaTag, type Match, type ModeloTrabalho, type StatusMatch } from "@/lib/types";

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
    >
      {erro && <Notice>{erro}</Notice>}
      {ongId && <Fila ongId={ongId} novoCadastro={cadastrados} />}
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

function NovoCandidato({ ongId, onCadastrado, onFechar }: { ongId: string; onCadastrado: () => void; onFechar: () => void }) {
  const [f, setF] = useState({
    nomeCompleto: "", dataNascimento: "", cep: "", modeloTrabalhoPref: "PRESENCIAL" as ModeloTrabalho,
  });
  const [tags, setTags] = useState<{ categoria: CategoriaTag; tag: string }[]>([]);
  const [novaTag, setNovaTag] = useState({ categoria: "VULNERABILIDADE" as CategoriaTag, tag: "" });
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
        body: { ...f, cep: f.cep.replace(/\D/g, ""), tags },   // coordenadas são obtidas no servidor, pelo CEP
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

      {erro && <Notice>{erro}</Notice>}
      <Button type="submit" loading={loading} className="w-full !py-3.5">Cadastrar candidato</Button>
    </form>
  );
}
