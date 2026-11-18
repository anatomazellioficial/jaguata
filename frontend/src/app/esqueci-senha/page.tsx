"use client";

import Link from "next/link";
import { AnimatePresence, m as motion } from "framer-motion";
import { useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice, spring } from "@/components/ui";
import { ApiError, api } from "@/lib/api";

export default function EsqueciSenhaPage() {
  const [email, setEmail] = useState("");
  const [enviado, setEnviado] = useState(false);
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setLoading(true);
    try {
      await api("auth/senha/esqueci", { method: "POST", body: { email } });
      setEnviado(true);   // a resposta é sempre a mesma, exista ou não a conta
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Não foi possível enviar agora.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthFrame
      title="Esqueci a senha"
      subtitle="Enviaremos um link para você criar uma nova."
      footer={<Link className="font-semibold text-brick hover:underline" href="/login">Voltar ao login</Link>}
    >
      <AnimatePresence mode="wait" initial={false}>
        {enviado ? (
          <motion.div key="ok" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={spring} className="space-y-4">
            <Notice kind="ok">Se este e-mail tiver uma conta, enviamos um link de redefinição. Ele vale por 30 minutos.</Notice>
            <p className="text-sm text-mute">Não chegou? Confira o spam e aguarde um minuto antes de pedir outro.</p>
          </motion.div>
        ) : (
          <motion.form key="form" onSubmit={submit} className="space-y-4" exit={{ opacity: 0 }}>
            <Field label="E-mail da conta" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            {erro && <Notice>{erro}</Notice>}
            <Button type="submit" loading={loading} className="w-full !py-3.5">Enviar link</Button>
          </motion.form>
        )}
      </AnimatePresence>
    </AuthFrame>
  );
}
