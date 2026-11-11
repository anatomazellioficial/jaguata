"use client";

import { AnimatePresence, motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { Shell } from "@/components/Shell";
import { Button, Field, Notice, ScoreBar, Select, StatusBadge } from "@/components/ui";
import { ApiError, api, getSession } from "@/lib/api";
import { ROTULO_MODELO, ROTULO_STATUS, type CategoriaTag, type Match, type ModeloTrabalho, type StatusMatch } from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");
const ABAS: StatusMatch[] = ["SUGERIDO", "ENVIADO", "ENTREVISTA", "CONTRATADO", "RECUSADO"];

function maxNascimento() {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 18);
  return d.toISOString().slice(0, 10);
}

export default function OngPage() {
  const [ongId, setOngId] = useState<string>();
  const [erro, setErro] = useState<string>();

  useEffect(() => {
    getSession()
      .then((s) => (s.ongId ? setOngId(s.ongId) : setErro("Esta conta não está vinculada a uma ONG.")))
      .catch((e) => setErro(msg(e)));
  }, []);

  return (
    <Shell title="Sua ONG" subtitle="Cadastre seus assistidos (18+) e envie os melhores perfis para as vagas sugeridas.">
      {erro && <Notice>{erro}</Notice>}
      {ongId && (
        <div className="grid gap-12 lg:grid-cols-[380px_1fr]">
          <NovoCandidato ongId={ongId} />
          <Fila ongId={ongId} />
        </div>
      )}
    </Shell>
  );
}

function Fila({ ongId }: { ongId: string }) {
  const [aba, setAba] = useState<StatusMatch>("SUGERIDO");
  const [matches, setMatches] = useState<Match[]>();
  const [erro, setErro] = useState<string>();

  const carregar = useCallback(async () => {
    setMatches(await api<Match[]>(`ongs/${ongId}/matches?status=${aba}`));
  }, [ongId, aba]);

  useEffect(() => { setMatches(undefined); carregar().catch((e) => setErro(msg(e))); }, [carregar]);

  async function mover(id: string, status: "ENVIADO" | "RECUSADO") {
    setErro(undefined);
    try { await api(`matches/${id}/status`, { method: "PATCH", body: { status } }); await carregar(); }
    catch (e) { setErro(msg(e)); }
  }

  return (
    <section>
      <h2 className="display mb-6 text-3xl">Funil</h2>
      <div className="mb-6 flex flex-wrap gap-2" role="tablist">
        {ABAS.map((s) => (
          <button key={s} role="tab" aria-selected={aba === s} onClick={() => setAba(s)}
            className={`rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-wider transition-colors ${aba === s ? "border-neon bg-neon text-navy" : "border-line text-mute hover:text-paper"}`}>
            {ROTULO_STATUS[s]}
          </button>
        ))}
      </div>
      {erro && <Notice>{erro}</Notice>}
      {matches && matches.length === 0 && (
        <Notice kind="info">
          {aba === "SUGERIDO"
            ? "Nenhuma sugestão por enquanto. Matches aparecem quando uma empresa busca candidatos e sua ONG está verificada."
            : "Nada nesta etapa."}
        </Notice>
      )}
      <ul className="space-y-3">
        <AnimatePresence initial={false}>
          {matches?.map((m) => (
            <motion.li key={m.id} layout initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, x: -20 }}
              className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-panel p-5">
              <div>
                <p className="text-lg font-bold">{m.candidatoNome}</p>
                <p className="mb-2 text-sm text-mute">para a vaga de {m.tituloVaga}</p>
                <ScoreBar score={m.matchScore} />
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge status={m.status} />
                {m.status === "SUGERIDO" && <>
                  <Button onClick={() => mover(m.id, "ENVIADO")}>Enviar à empresa</Button>
                  <Button variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Descartar</Button>
                </>}
                {m.status === "ENVIADO" && <Button variant="danger" onClick={() => mover(m.id, "RECUSADO")}>Retirar</Button>}
              </div>
            </motion.li>
          ))}
        </AnimatePresence>
      </ul>
    </section>
  );
}

function NovoCandidato({ ongId }: { ongId: string }) {
  const [f, setF] = useState({
    nomeCompleto: "", dataNascimento: "", cep: "", lat: "", lng: "", modeloTrabalhoPref: "PRESENCIAL" as ModeloTrabalho,
  });
  const [tags, setTags] = useState<{ categoria: CategoriaTag; tag: string }[]>([]);
  const [novaTag, setNovaTag] = useState({ categoria: "VULNERABILIDADE" as CategoriaTag, tag: "" });
  const [erro, setErro] = useState<string>();
  const [erros, setErros] = useState<Record<string, string>>({});
  const [ok, setOk] = useState<string>();
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
    setErro(undefined); setErros({}); setOk(undefined); setLoading(true);
    try {
      await api(`ongs/${ongId}/candidatos`, {
        method: "POST",
        body: { ...f, cep: f.cep.replace(/\D/g, ""), lat: Number(f.lat), lng: Number(f.lng), tags },
      });
      setOk("Candidato cadastrado.");
      setF({ nomeCompleto: "", dataNascimento: "", cep: "", lat: "", lng: "", modeloTrabalhoPref: f.modeloTrabalhoPref });
      setTags([]);
    } catch (err) {
      setErro(msg(err));
      if (err instanceof ApiError) setErros(err.campos ?? {});
    } finally { setLoading(false); }
  }

  return (
    <form onSubmit={submit} className="h-fit space-y-4 rounded-3xl border border-line bg-panel p-6">
      <h2 className="display text-3xl">Novo candidato</h2>
      <Field label="Nome completo" required value={f.nomeCompleto} onChange={set("nomeCompleto")} error={erros.nomeCompleto} />
      <Field label="Data de nascimento" type="date" required max={maxNascimento()} value={f.dataNascimento}
        onChange={set("dataNascimento")} hint="Apenas maiores de 18 anos." error={erros.dataNascimento} />
      <Field label="CEP" inputMode="numeric" required value={f.cep} onChange={set("cep")} error={erros.cep} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Latitude" required inputMode="decimal" value={f.lat} onChange={set("lat")} error={erros.lat} />
        <Field label="Longitude" required inputMode="decimal" value={f.lng} onChange={set("lng")} error={erros.lng} />
      </div>
      <Select label="Preferência de trabalho" value={f.modeloTrabalhoPref} onChange={set("modeloTrabalhoPref")}>
        {Object.entries(ROTULO_MODELO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </Select>

      <fieldset className="space-y-2">
        <legend className="mb-1.5 text-xs font-semibold uppercase tracking-wider text-mute">Tags</legend>
        <div className="flex gap-2">
          <select aria-label="Categoria da tag" value={novaTag.categoria}
            onChange={(e) => setNovaTag((s) => ({ ...s, categoria: e.target.value as CategoriaTag }))}
            className="rounded-xl border border-line bg-panel px-3 text-xs text-paper">
            <option value="VULNERABILIDADE">Vulnerabilidade</option>
            <option value="DEMOGRAFIA">Demografia</option>
            <option value="SKILL">Skill</option>
          </select>
          <input aria-label="Tag" placeholder="PCD, Egresso, 50+…" value={novaTag.tag}
            onChange={(e) => setNovaTag((s) => ({ ...s, tag: e.target.value }))}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addTag(); } }}
            className="min-w-0 flex-1 rounded-xl border border-line bg-panel px-3 py-2 text-sm outline-none focus:border-orange" />
          <Button type="button" variant="ghost" onClick={addTag}>+</Button>
        </div>
        <div className="flex flex-wrap gap-2">
          {tags.map((t) => (
            <button key={t.categoria + t.tag} type="button" onClick={() => setTags((s) => s.filter((x) => x !== t))}
              className="rounded-full border border-line px-3 py-1 text-xs hover:border-danger hover:text-danger" title="Remover">
              {t.tag} ✕
            </button>
          ))}
        </div>
      </fieldset>

      {erro && <Notice>{erro}</Notice>}
      {ok && <Notice kind="ok">{ok}</Notice>}
      <Button type="submit" loading={loading} className="w-full">Cadastrar</Button>
    </form>
  );
}
