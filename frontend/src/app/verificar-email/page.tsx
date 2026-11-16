"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import { Suspense, useEffect, useRef, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Notice, spring } from "@/components/ui";
import { ApiError, api } from "@/lib/api";

function Verificar() {
  const token = useSearchParams().get("token");
  const [estado, setEstado] = useState<"carregando" | "ok" | "erro">("carregando");
  const [erro, setErro] = useState<string>();
  const feito = useRef(false);   // o link é de uso único: não pode ser consumido duas vezes (StrictMode, re-render)

  useEffect(() => {
    if (feito.current) return;
    feito.current = true;
    history.replaceState(null, "", "/verificar-email");   // tira o token da barra de endereço e do histórico
    if (!token) {
      setErro("Link incompleto. Abra o link do e-mail novamente.");
      setEstado("erro");
      return;
    }
    api("auth/email/verificar", { method: "POST", body: { token } })
      .then(() => setEstado("ok"))
      .catch((e) => {
        setErro(e instanceof ApiError ? e.message : "Não foi possível confirmar agora.");
        setEstado("erro");
      });
  }, [token]);

  return (
    <AuthFrame
      title={estado === "ok" ? "E-mail confirmado" : estado === "erro" ? "Não deu certo" : "Confirmando…"}
      footer={<Link className="font-semibold text-brick hover:underline" href="/login">Ir para o login</Link>}
    >
      {estado === "carregando" && (
        <motion.div aria-hidden className="mx-auto h-8 w-8 rounded-full border-[3px] border-orange border-t-transparent"
          animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 0.9, ease: "linear" }} />
      )}
      {estado === "ok" && (
        <div className="space-y-5 text-center">
          <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", stiffness: 260, damping: 14 }}
            className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-navy text-3xl text-cream">✓</motion.div>
          <p className="text-mute">Sua conta está ativa. Agora é só entrar.</p>
          <Link href="/login"><Button className="w-full !py-3.5">Entrar</Button></Link>
        </div>
      )}
      {estado === "erro" && (
        <div className="space-y-4">
          <Notice>{erro}</Notice>
          <p className="text-sm text-mute">O link vale por 24 horas e só pode ser usado uma vez. Entre com a sua senha para pedir um novo.</p>
          <Link href="/login"><Button variant="ghost" className="w-full">Pedir novo link</Button></Link>
        </div>
      )}
      <motion.span transition={spring} />
    </AuthFrame>
  );
}

export default function VerificarEmailPage() {
  return (
    <Suspense>
      <Verificar />
    </Suspense>
  );
}
