"use client";

import QRCode from "qrcode";
import { useCallback, useEffect, useState } from "react";
import { Button, Card, Field, Notice } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { Check } from "lucide-react";

const msg = (e: unknown) => (e instanceof ApiError ? e.message : "Algo deu errado.");

/** Segundo fator (TOTP) do administrador: o QR code é gerado aqui no navegador; o segredo nunca vai a terceiros. */
export function SegundoFator() {
  const [ativo, setAtivo] = useState<boolean>();
  const [cfg, setCfg] = useState<{ segredo: string; qr: string }>();
  const [codigo, setCodigo] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string>();
  const [ok, setOk] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [desativando, setDesativando] = useState(false);

  const carregar = useCallback(async () => {
    setAtivo((await api<{ ativo: boolean }>("conta/2fa")).ativo);
  }, []);
  useEffect(() => { carregar().catch((e) => setErro(msg(e))); }, [carregar]);

  async function configurar() {
    setBusy(true); setErro(undefined); setOk(undefined);
    try {
      const r = await api<{ segredo: string; otpauthUri: string }>("conta/2fa/configurar", { method: "POST" });
      setCfg({ segredo: r.segredo, qr: await QRCode.toDataURL(r.otpauthUri, { margin: 1, width: 220 }) });
    } catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }

  async function ativar(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setErro(undefined);
    try {
      await api("conta/2fa/ativar", { method: "POST", body: { codigo } });
      setCfg(undefined); setCodigo(""); setOk("Segundo fator ativado. No próximo login será pedido o código do app.");
      await carregar();
    } catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }

  async function desativar(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setErro(undefined);
    try {
      await api("conta/2fa/desativar", { method: "POST", body: { senha, codigo } });
      setDesativando(false); setSenha(""); setCodigo(""); setOk("Segundo fator desativado.");
      await carregar();
    } catch (e) { setErro(msg(e)); } finally { setBusy(false); }
  }

  return (
    <Card className="p-6">
      <h2 className="title mb-1 text-2xl">Verificação em duas etapas</h2>
      <p className="mb-4 text-sm text-mute">
        Administradores acessam dados sensíveis. Com o segundo fator, só a senha não basta: é preciso o código de 6 dígitos
        de um app autenticador (Google Authenticator, Authy, 1Password…).
      </p>
      {ativo === undefined && !erro && <p className="text-sm text-mute">Carregando…</p>}
      {ok && <div className="mb-3"><Notice kind="ok">{ok}</Notice></div>}
      {erro && <div className="mb-3"><Notice>{erro}</Notice></div>}

      {ativo === false && !cfg && <Button onClick={configurar} loading={busy}>Ativar verificação em duas etapas</Button>}

      {ativo === false && cfg && (
        <form onSubmit={ativar} className="space-y-4">
          <p className="text-sm">1. Leia o QR code com o app autenticador (ou digite o segredo).</p>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={cfg.qr} alt="QR code para o app autenticador" width={220} height={220} className="rounded-2xl bg-white p-2" />
          <p className="break-all rounded-xl bg-navy/[.05] px-3 py-2 font-mono text-xs">{cfg.segredo}</p>
          <p className="text-sm">2. Digite o código que o app mostra agora para confirmar.</p>
          <Field label="Código de 6 dígitos" inputMode="numeric" autoComplete="one-time-code" pattern="\d{6}" maxLength={6}
            required value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))} />
          <div className="flex gap-2">
            <Button type="submit" loading={busy} disabled={codigo.length !== 6}>Confirmar e ativar</Button>
            <Button type="button" variant="ghost" onClick={() => { setCfg(undefined); setCodigo(""); }}>Cancelar</Button>
          </div>
        </form>
      )}

      {ativo === true && !desativando && (
        <div className="flex items-center justify-between gap-3">
          <span className="inline-flex items-center gap-1 rounded-full bg-sun/50 px-3 py-1 text-xs font-semibold"><Check className="h-3.5 w-3.5" strokeWidth={3} aria-hidden /> Ativa</span>
          <Button variant="ghost" size="sm" onClick={() => { setDesativando(true); setOk(undefined); }}>Desativar</Button>
        </div>
      )}
      {ativo === true && desativando && (
        <form onSubmit={desativar} className="space-y-4">
          <Field label="Senha atual" type="password" autoComplete="current-password" required value={senha}
            onChange={(e) => setSenha(e.target.value)} />
          <Field label="Código de 6 dígitos (um código novo, ainda não usado)" inputMode="numeric" autoComplete="one-time-code"
            maxLength={6} required value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, ""))} />
          <div className="flex gap-2">
            <Button type="submit" variant="danger" loading={busy} disabled={codigo.length !== 6 || !senha}>Desativar</Button>
            <Button type="button" variant="ghost" onClick={() => setDesativando(false)}>Cancelar</Button>
          </div>
        </form>
      )}
    </Card>
  );
}
