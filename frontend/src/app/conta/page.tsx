"use client";

import Link from "next/link";
import { AnimatePresence, m as motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";
import { Shell } from "@/components/Shell";
import { Button, Card, Field, Notice, Skeleton, Switch, spring } from "@/components/ui";
import { SegundoFator } from "@/components/SegundoFator";
import { ApiError, api, baixar, logout } from "@/lib/api";
import { homeFor } from "@/lib/jwt";
import type { ContaDados } from "@/lib/types";
import { ArrowLeft, Check } from "lucide-react";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");
const data = (iso: string | null) => (iso ? new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) : "—");

function Linha({ rotulo, valor }: { rotulo: string; valor: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-3">
      <dt className="text-sm text-mute">{rotulo}</dt>
      <dd className="text-right text-sm font-medium">{valor}</dd>
    </div>
  );
}

export default function ContaPage() {
  const [dados, setDados] = useState<ContaDados>();
  const [erro, setErro] = useState<string>();

  const carregar = useCallback(async () => setDados(await api<ContaDados>("conta/dados")), []);
  useEffect(() => { carregar().catch((e) => setErro(msg(e))); }, [carregar]);

  return (
    <Shell
      title="Sua conta"
      subtitle="Seus dados de acesso, segurança e direitos de privacidade."
      action={dados ? <Link href={homeFor(dados.papel)}><Button size="sm" variant="ghost"><ArrowLeft className="h-4 w-4" aria-hidden /> Painel</Button></Link> : undefined}
    >
      {erro && <Notice>{erro}</Notice>}
      {!dados && !erro && <div className="grid gap-4 lg:grid-cols-2"><Skeleton className="h-64" /><Skeleton className="h-64" /></div>}
      {dados && (
        <div className="grid items-start gap-4 lg:grid-cols-2">
          <DadosCard dados={dados} />
          <div className="space-y-4">
            <SenhaCard />
            <SessoesCard ativas={dados.sessoesAtivas} />
          </div>
          <PrivacidadeCard dados={dados} />
        </div>
      )}
      {dados && dados.papel !== "ADMIN" && <NotificacoesCard dados={dados} onMudou={carregar} />}
      {dados?.papel === "ADMIN" && <div className="mt-4"><SegundoFator /></div>}
      {dados && (
        <Card className="mt-4 flex items-center justify-between gap-4 p-6">
          <div>
            <h2 className="title text-2xl">Sair</h2>
            <p className="text-sm text-mute">Encerra a sessão neste dispositivo.</p>
          </div>
          <Button variant="ghost" onClick={() => logout("/login")}>Sair</Button>
        </Card>
      )}
    </Shell>
  );
}

function NotificacoesCard({ dados, onMudou }: { dados: ContaDados; onMudou: () => Promise<void> }) {
  const ativo = dados.notificacoesEmail;
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState<string>();
  async function alternar(campo: "ativas" | "sugestoes" | "andamento" | "resumoDiario", valor: boolean) {
    setBusy(true); setErro(undefined);
    try { await api("conta/notificacoes", { method: "PUT", body: { [campo]: valor } }); await onMudou(); }
    catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }
  return (
    <Card className="mt-4 p-6">
      <h2 className="title mb-1 text-2xl">Avisos por e-mail</h2>
      <p className="mb-4 text-sm text-mute">
        Novas sugestões, candidato enviado, entrevista e contratação. Os e-mails nunca trazem nomes de candidatos.
      </p>
      {erro && <div className="mb-3"><Notice>{erro}</Notice></div>}
      <div className="flex items-center justify-between gap-4 text-sm font-medium">
        <span>{ativo ? "Ligados" : "Desligados"}</span>
        <Switch label="Avisos por e-mail" checked={ativo} disabled={busy} onChange={(v) => alternar("ativas", v)} />
      </div>
      {ativo && (
        <div className="mt-4 space-y-3 border-t border-navy/5 pt-4 text-sm">
          {dados.papel === "ONG" && (
            <div className="flex items-center justify-between gap-4">
              <span>Novas sugestões de candidatos</span>
              <Switch label="Novas sugestões" checked={dados.notifSugestoes} disabled={busy} onChange={(v) => alternar("sugestoes", v)} />
            </div>
          )}
          <div className="flex items-center justify-between gap-4">
            <span>{dados.papel === "ONG" ? "Entrevistas e contratações" : "Candidatos enviados pelas ONGs"}</span>
            <Switch label="Andamento do funil" checked={dados.notifAndamento} disabled={busy} onChange={(v) => alternar("andamento", v)} />
          </div>
          <div className="flex items-center justify-between gap-4">
            <span>Receber tudo em <b>um resumo por dia</b> (08:00), em vez de e-mails na hora</span>
            <Switch label="Resumo diário" checked={dados.notifResumoDiario} disabled={busy} onChange={(v) => alternar("resumoDiario", v)} />
          </div>
        </div>
      )}
    </Card>
  );
}

function DadosCard({ dados }: { dados: ContaDados }) {
  const entidade = dados.empresa ?? dados.ong;
  return (
    <Card className="p-6">
      <h2 className="title mb-2 text-2xl">Dados da conta</h2>
      <dl className="divide-y divide-navy/5">
        <Linha rotulo="E-mail" valor={dados.email} />
        <Linha rotulo="Perfil" valor={dados.papel === "ADMIN" ? "Administrador" : dados.papel === "ONG" ? "ONG" : "Empresa"} />
        {entidade && <Linha rotulo="Organização" valor={entidade.razaoSocial} />}
        {dados.ong && <Linha rotulo="Verificação da ONG" valor={dados.ong.verified ? <span className="inline-flex items-center gap-1"><Check className="h-3.5 w-3.5" strokeWidth={3} aria-hidden /> Verificada</span> : "Pendente"} />}
        <Linha rotulo="E-mail confirmado" valor={dados.emailVerificado ? <span className="inline-flex items-center gap-1"><Check className="h-3.5 w-3.5" strokeWidth={3} aria-hidden /> Sim</span> : "Não"} />
        <Linha rotulo="Conta criada em" valor={data(dados.criadaEm)} />
        <Linha rotulo="Termos aceitos" valor={`versão ${dados.termosVersao ?? "—"} · ${data(dados.termosAceitosEm)}`} />
        <Linha rotulo="Senha alterada em" valor={data(dados.senhaAlteradaEm)} />
      </dl>
    </Card>
  );
}

function SenhaCard() {
  const [f, setF] = useState({ atual: "", nova: "", confirma: "" });
  const [erro, setErro] = useState<string>();
  const [ok, setOk] = useState(false);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    if (f.nova !== f.confirma) return setErro("As senhas novas não são iguais.");
    setLoading(true);
    try {
      await api("conta/senha", { method: "POST", body: { senhaAtual: f.atual, novaSenha: f.nova } });
      setOk(true);
      // o servidor encerrou todas as sessões: voltamos ao login
      setTimeout(() => logout("/login?senha=alterada"), 1400);
    } catch (err) {
      setErro(msg(err));
      setLoading(false);
    }
  }

  return (
    <Card className="p-6">
      <h2 className="title mb-1 text-2xl">Alterar senha</h2>
      <p className="mb-5 text-sm text-mute">Ao trocar, todos os dispositivos são desconectados.</p>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Senha atual" type="password" autoComplete="current-password" required value={f.atual} onChange={(e) => setF({ ...f, atual: e.target.value })} />
        <Field label="Nova senha" type="password" autoComplete="new-password" required minLength={10} maxLength={72}
          hint="Mínimo de 10 caracteres." value={f.nova} onChange={(e) => setF({ ...f, nova: e.target.value })} />
        <Field label="Repita a nova senha" type="password" autoComplete="new-password" required value={f.confirma}
          onChange={(e) => setF({ ...f, confirma: e.target.value })} />
        {erro && <Notice>{erro}</Notice>}
        {ok && <Notice kind="ok">Senha alterada. Redirecionando para o login…</Notice>}
        <Button type="submit" loading={loading} disabled={ok}>Salvar senha</Button>
      </form>
    </Card>
  );
}

function SessoesCard({ ativas }: { ativas: number }) {
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState<string>();
  return (
    <Card className="p-6">
      <h2 className="title mb-1 text-2xl">Sessões</h2>
      <p className="mb-4 text-sm text-mute">
        {ativas} {ativas === 1 ? "sessão ativa" : "sessões ativas"}. Se perdeu um aparelho ou desconfia de algo, desconecte todos.
      </p>
      {erro && <div className="mb-3"><Notice>{erro}</Notice></div>}
      <Button variant="ghost" loading={loading} onClick={async () => {
        setLoading(true);
        try { await api("conta/sessoes/encerrar", { method: "POST" }); await logout("/login"); }
        catch (e) { setErro(msg(e)); setLoading(false); }
      }}>Sair de todos os dispositivos</Button>
    </Card>
  );
}

function PrivacidadeCard({ dados }: { dados: ContaDados }) {
  const [abrir, setAbrir] = useState(false);
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function excluir(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setLoading(true);
    try {
      await api("conta", { method: "DELETE", body: { senha } });
      await logout("/");
    } catch (err) {
      setErro(msg(err));
      setLoading(false);
    }
  }

  return (
    <Card className="p-6 lg:col-span-2">
      <h2 className="title mb-1 text-2xl">Privacidade e seus dados</h2>
      <p className="mb-5 max-w-2xl text-sm text-mute">
        Você tem direito de acessar e excluir os seus dados (LGPD, art. 18). Leia a{" "}
        <Link href="/privacidade" className="font-semibold text-brick underline">Política de Privacidade</Link>.
      </p>
      <div className="flex flex-wrap gap-3">
        <Button variant="ghost" onClick={() => baixar("conta/dados", "meus-dados-jaguata.json")}>Baixar meus dados</Button>
        {dados.papel !== "ADMIN" && (
          <Button variant="danger" onClick={() => setAbrir((v) => !v)}>{abrir ? "Cancelar" : "Excluir minha conta"}</Button>
        )}
      </div>

      <AnimatePresence initial={false}>
        {abrir && (
          <motion.form onSubmit={excluir} initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }} transition={spring} className="overflow-hidden">
            <div className="mt-5 max-w-md space-y-4 rounded-2xl bg-brick/[.06] p-5">
              <p className="text-sm">
                Isto apaga o seu acesso (login, sessões e links pendentes) e não pode ser desfeito. Os dados cadastrais da
                organização, que são de pessoa jurídica, permanecem. Confirme com a sua senha.
              </p>
              <Field label="Senha" type="password" autoComplete="current-password" required value={senha} onChange={(e) => setSenha(e.target.value)} />
              {erro && <Notice>{erro}</Notice>}
              <Button type="submit" variant="danger" loading={loading}>Excluir definitivamente</Button>
            </div>
          </motion.form>
        )}
      </AnimatePresence>
    </Card>
  );
}
