"use client";

import { useEffect, useState, type ReactNode } from "react";
import { Button, Card, Notice, Skeleton } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import type { EmpresaImpacto, OngImpacto } from "@/lib/types";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");

const dec = (n: number | null | undefined, sufixo = "") =>
  n == null ? "—" : `${n.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}${sufixo}`;

function Tile({ rotulo, valor, destaque = false }: { rotulo: string; valor: ReactNode; destaque?: boolean }) {
  return (
    <div className={`rounded-[22px] p-4 ${destaque ? "bg-sun/50" : "bg-white/70"}`}>
      <div className="title text-3xl tabular-nums">{valor}</div>
      <div className="mt-1 text-xs font-medium text-mute">{rotulo}</div>
    </div>
  );
}

function Moldura({ titulo, geradoEm, children }: { titulo: string; geradoEm?: string; children: ReactNode }) {
  return (
    <section aria-label={titulo}>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="title text-3xl">{titulo}</h2>
          {geradoEm && (
            <p className="mt-1 text-sm text-mute">
              Gerado em {new Date(geradoEm).toLocaleString("pt-BR", { dateStyle: "long", timeStyle: "short" })}
            </p>
          )}
        </div>
        <Button size="sm" variant="ghost" onClick={() => window.print()} className="print:hidden">Imprimir / salvar PDF</Button>
      </div>
      {children}
      <p className="mt-6 max-w-2xl text-xs text-mute">
        Este relatório traz apenas totais. Por proteção das pessoas atendidas, não há recortes por gênero, raça/cor,
        deficiência ou qualquer outro atributo individual.
      </p>
    </section>
  );
}

function Lista({ titulo, itens, vazio }: { titulo: string; itens: { nome: string; n: number }[]; vazio: string }) {
  return (
    <Card className="p-5">
      <h3 className="title mb-2 text-xl">{titulo}</h3>
      {itens.length === 0 ? <p className="text-sm text-mute">{vazio}</p> : (
        <ul className="divide-y divide-navy/5">
          {itens.map((i) => (
            <li key={i.nome} className="flex items-center justify-between gap-3 py-2 text-sm">
              <span className="min-w-0 truncate">{i.nome}</span>
              <span className="font-semibold tabular-nums">{i.n}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function useRelatorio<T>(caminho: string) {
  const [dados, setDados] = useState<T>();
  const [erro, setErro] = useState<string>();
  useEffect(() => { api<T>(caminho).then(setDados).catch((e) => setErro(msg(e))); }, [caminho]);
  return { dados, erro };
}

export function EmpresaImpactoView({ empresaId }: { empresaId: string }) {
  const { dados: d, erro } = useRelatorio<EmpresaImpacto>(`empresas/${empresaId}/impacto`);
  if (erro) return <Notice>{erro}</Notice>;
  if (!d) return <div className="grid gap-3 sm:grid-cols-3"><Skeleton className="h-24" /><Skeleton className="h-24" /><Skeleton className="h-24" /></div>;
  return (
    <Moldura titulo="Impacto das suas contratações" geradoEm={d.geradoEm}>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Tile destaque rotulo="Contratações" valor={d.contratacoes} />
        <Tile rotulo="ONGs parceiras" valor={d.ongsParceiras} />
        <Tile rotulo="Tempo médio até contratar" valor={dec(d.tempoMedioAteContratacaoDias, " d")} />
        <Tile rotulo="Vagas preenchidas" valor={`${d.vagasPreenchidas}/${d.vagasTotal}`} />
        <Tile rotulo="Vagas abertas" valor={d.vagasAbertas} />
        <Tile rotulo="Candidatos sugeridos" valor={d.sugestoes} />
        <Tile rotulo="Enviados pelas ONGs" valor={d.candidatosEnviados} />
        <Tile rotulo="Entrevistas" valor={d.entrevistas} />
        <Tile rotulo="Vagas afirmativas" valor={d.vagasAfirmativas} />
        <Tile rotulo="Contratações em vagas afirmativas" valor={d.contratacoesEmVagasAfirmativas} />
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Lista titulo="Contratações por ONG" vazio="Ainda não há contratações."
          itens={d.contratacoesPorOng.map((o) => ({ nome: o.ong, n: o.contratacoes }))} />
      </div>
    </Moldura>
  );
}

export function OngImpactoView({ ongId }: { ongId: string }) {
  const { dados: d, erro } = useRelatorio<OngImpacto>(`ongs/${ongId}/impacto`);
  if (erro) return <Notice>{erro}</Notice>;
  if (!d) return <div className="grid gap-3 sm:grid-cols-3"><Skeleton className="h-24" /><Skeleton className="h-24" /><Skeleton className="h-24" /></div>;
  return (
    <Moldura titulo="Impacto da sua ONG" geradoEm={d.geradoEm}>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Tile destaque rotulo="Contratações" valor={d.contratacoes} />
        <Tile rotulo="Taxa de colocação" valor={dec(d.taxaColocacaoPct, "%")} />
        <Tile rotulo="Tempo médio até contratar" valor={dec(d.tempoMedioAteContratacaoDias, " d")} />
        <Tile rotulo="Empresas parceiras" valor={d.empresasParceiras} />
        <Tile rotulo="Pessoas cadastradas" valor={d.candidatosAtivos} />
        <Tile rotulo="Aguardando envio" valor={d.aguardandoEnvio} />
        <Tile rotulo="Em análise na empresa" valor={d.emAnalise} />
        <Tile rotulo="Em entrevista" valor={d.emEntrevista} />
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Lista titulo="Contratações por setor" vazio="Ainda não há contratações."
          itens={d.contratacoesPorSetor.map((s) => ({ nome: s.setor, n: s.contratacoes }))} />
      </div>
    </Moldura>
  );
}
