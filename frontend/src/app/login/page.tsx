"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice } from "@/components/ui";
import { ApiError, login } from "@/lib/api";
import { homeFor } from "@/lib/jwt";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setLoading(true);
    try {
      const s = await login(email, senha);
      const next = params.get("next");
      // só aceita redirecionamento interno (evita open redirect)
      router.push(next && next.startsWith("/") && !next.startsWith("//") ? next : homeFor(s.papel));
      router.refresh();
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Não foi possível entrar.");
      setLoading(false);
    }
  }

  return (
    <AuthFrame title="Entrar" footer={<>Ainda não tem conta? <Link className="text-brick underline" href="/registro">Cadastre-se</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="E-mail" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field label="Senha" type="password" autoComplete="current-password" required value={senha} onChange={(e) => setSenha(e.target.value)} />
        {erro && <Notice>{erro}</Notice>}
        <Button type="submit" loading={loading} className="w-full">Entrar</Button>
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
