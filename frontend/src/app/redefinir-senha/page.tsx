"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import { Suspense, useEffect, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice } from "@/components/ui";
import { ApiError, api } from "@/lib/api";

function Redefinir() {
  const param = useSearchParams().get("token");
  const [token, setToken] = useState<string | null>(null);
  const [nova, setNova] = useState("");
  const [confirma, setConfirma] = useState("");
  const [erro, setErro] = useState<string>();
  const [feito, setFeito] = useState(false);
  const [loading, setLoading] = useState(false);

  // guarda o token em memória e o remove da barra de endereço/histórico
  useEffect(() => {
    if (param) {
      setToken(param);
      history.replaceState(null, "", "/redefinir-senha");
    }
  }, [param]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    if (nova !== confirma) return setErro("As senhas não são iguais.");
    setLoading(true);
    try {
      await api("auth/senha/redefinir", { method: "POST", body: { token, novaSenha: nova } });
      setFeito(true);
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Não foi possível redefinir agora.");
    } finally {
      setLoading(false);
    }
  }

  if (feito) {
    return (
      <AuthFrame title="Senha alterada" footer={<span>Todas as sessões foram encerradas por segurança.</span>}>
        <div className="space-y-5 text-center">
          <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", stiffness: 260, damping: 14 }}
            className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-navy text-3xl text-cream">✓</motion.div>
          <Link href="/login"><Button className="w-full !py-3.5">Entrar com a nova senha</Button></Link>
        </div>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame
      title="Nova senha"
      subtitle="Escolha uma senha longa e única."
      footer={<Link className="font-semibold text-brick hover:underline" href="/esqueci-senha">Pedir um novo link</Link>}
    >
      {!token && !param ? (
        <Notice>Link incompleto. Abra o link do e-mail novamente ou peça um novo.</Notice>
      ) : (
        <form onSubmit={submit} className="space-y-4">
          <Field label="Nova senha" type="password" autoComplete="new-password" required minLength={10} maxLength={72}
            hint="Mínimo de 10 caracteres. Evite senhas comuns e o seu e-mail." value={nova} onChange={(e) => setNova(e.target.value)} />
          <Field label="Repita a nova senha" type="password" autoComplete="new-password" required value={confirma}
            onChange={(e) => setConfirma(e.target.value)} />
          {erro && <Notice>{erro}</Notice>}
          <Button type="submit" loading={loading} className="w-full !py-3.5">Salvar nova senha</Button>
        </form>
      )}
    </AuthFrame>
  );
}

export default function RedefinirSenhaPage() {
  return (
    <Suspense>
      <Redefinir />
    </Suspense>
  );
}
