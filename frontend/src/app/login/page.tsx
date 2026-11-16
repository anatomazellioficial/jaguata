"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Suspense, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice, spring } from "@/components/ui";
import { ApiError, api, login } from "@/lib/api";
import { homeFor } from "@/lib/jwt";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string>();
  const [naoVerificado, setNaoVerificado] = useState(false);
  const [reenvio, setReenvio] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setReenvio(undefined);
    setNaoVerificado(false);
    setLoading(true);
    try {
      const s = await login(email, senha);
      const next = params.get("next");
      // só aceita redirecionamento interno (evita open redirect)
      router.push(next && next.startsWith("/") && !next.startsWith("//") ? next : homeFor(s.papel));
      router.refresh();
    } catch (err) {
      setNaoVerificado(err instanceof ApiError && err.codigo === "EMAIL_NAO_VERIFICADO");
      setErro(err instanceof ApiError ? err.message : "Não foi possível entrar.");
      setLoading(false);
    }
  }

  async function reenviar() {
    try {
      await api("auth/email/reenviar", { method: "POST", body: { email } });
      setReenvio("Pronto! Se houver uma confirmação pendente, enviamos um novo link. Confira também o spam.");
    } catch (err) {
      setReenvio(err instanceof ApiError ? err.message : "Não foi possível reenviar agora.");
    }
  }

  return (
    <AuthFrame
      title="Entrar"
      subtitle={params.get("senha") === "alterada" ? "Senha alterada. Entre com a nova senha." : "Bem-vindo de volta."}
      footer={<>Ainda não tem conta? <Link className="font-semibold text-brick hover:underline" href="/registro">Cadastre-se</Link></>}
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label="E-mail" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <div>
          <Field label="Senha" type="password" autoComplete="current-password" required value={senha} onChange={(e) => setSenha(e.target.value)} />
          <Link href="/esqueci-senha" className="mt-2 inline-block text-xs font-semibold text-brick hover:underline">Esqueci minha senha</Link>
        </div>
        {erro && <Notice>{erro}</Notice>}
        <AnimatePresence>
          {naoVerificado && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
              transition={spring} className="space-y-3 overflow-hidden">
              <Button type="button" variant="ghost" className="w-full" onClick={reenviar}>Reenviar e-mail de confirmação</Button>
              {reenvio && <Notice kind="ok">{reenvio}</Notice>}
            </motion.div>
          )}
        </AnimatePresence>
        <Button type="submit" loading={loading} className="mt-2 w-full !py-3.5">Entrar</Button>
      </form>
    </AuthFrame>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
