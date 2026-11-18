"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AnimatePresence, m as motion } from "framer-motion";
import { Suspense, useState } from "react";
import { CnpjField } from "@/components/Lookups";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice, Segmented, Select, spring } from "@/components/ui";
import { ApiError, api } from "@/lib/api";
import { MailCheck } from "lucide-react";

function RegistroForm() {
  const params = useSearchParams();
  const [tipo, setTipo] = useState<"empresa" | "ong">(params.get("tipo") === "ong" ? "ong" : "empresa");
  const [f, setF] = useState({ email: "", senha: "", cnpj: "", nivelCapacidade: "NIVEL_1" });
  const [aceite, setAceite] = useState(false);
  const [cnpjOk, setCnpjOk] = useState(false);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [concluido, setConcluido] = useState<string>();
  const [reenvio, setReenvio] = useState<string>();

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((s) => ({ ...s, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setErros({});
    setLoading(true);
    try {
      // Só o CNPJ vai ao servidor: razão social, CNAE, porte e endereço vêm da Receita Federal.
      await api(`auth/registro/${tipo}`, {
        method: "POST",
        body: {
          email: f.email, senha: f.senha, cnpj: f.cnpj, aceiteTermos: aceite,
          ...(tipo === "ong" ? { nivelCapacidade: f.nivelCapacidade } : {}),
        },
      });
      setConcluido(f.email);   // o login só é liberado depois de confirmar o e-mail
    } catch (err) {
      if (err instanceof ApiError) {
        setErro(err.message);
        setErros(err.campos ?? {});
      } else setErro("Não foi possível concluir o cadastro.");
    } finally {
      setLoading(false);
    }
  }

  async function reenviar() {
    try {
      await api("auth/email/reenviar", { method: "POST", body: { email: concluido } });
      setReenvio("Novo link enviado (se o anterior ainda não foi usado).");
    } catch (err) {
      setReenvio(err instanceof ApiError ? err.message : "Não foi possível reenviar agora.");
    }
  }

  if (concluido) {
    return (
      <AuthFrame title="Confirme seu e-mail" footer={<>Já confirmou? <Link className="font-semibold text-brick hover:underline" href="/login">Entrar</Link></>}>
        <div className="space-y-5 text-center">
          <motion.div initial={{ scale: 0, rotate: -20 }} animate={{ scale: 1, rotate: 0 }} transition={{ type: "spring", stiffness: 240, damping: 14 }}
            className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-sun/60"><MailCheck className="h-8 w-8" aria-hidden /></motion.div>
          <p className="text-mute">
            Enviamos um link para <strong className="text-navy">{concluido}</strong>. Abra-o para ativar a conta. O link vale por 24 horas.
          </p>
          {tipo === "ong" && <Notice kind="info">Depois, a verificação da sua ONG pela equipe libera as sugestões de candidatos.</Notice>}
          <Button variant="ghost" className="w-full" onClick={reenviar}>Reenviar e-mail</Button>
          {reenvio && <Notice kind="ok">{reenvio}</Notice>}
        </div>
      </AuthFrame>
    );
  }

  return (
    <AuthFrame
      title="Criar conta"
      subtitle="Informe o CNPJ e buscamos o resto na Receita Federal."
      footer={<>Já tem conta? <Link className="font-semibold text-brick hover:underline" href="/login">Entrar</Link></>}
    >
      <div className="mb-6 flex justify-center">
        <Segmented label="Tipo de conta" value={tipo} onChange={setTipo}
          options={[{ value: "empresa", label: "Sou empresa" }, { value: "ong", label: "Sou ONG" }]} />
      </div>

      <form onSubmit={submit} className="space-y-4">
        <CnpjField tipo={tipo} value={f.cnpj} onChange={(v) => setF((s) => ({ ...s, cnpj: v }))}
          error={erros.cnpj} onPodeCadastrar={setCnpjOk} />

        <Field label="E-mail" type="email" autoComplete="email" required value={f.email} onChange={set("email")} error={erros.email} />
        <Field label="Senha" type="password" autoComplete="new-password" required minLength={10} maxLength={72}
          hint="Mínimo de 10 caracteres. Evite senhas comuns e o seu e-mail." value={f.senha} onChange={set("senha")} error={erros.senha} />

        <AnimatePresence initial={false}>
          {tipo === "ong" && (
            <motion.div key="nivel" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
              className="space-y-4 overflow-hidden">
              <Select label="Nível de capacidade" value={f.nivelCapacidade} onChange={set("nivelCapacidade")}>
                <option value="NIVEL_1">Nível 1</option><option value="NIVEL_2">Nível 2</option><option value="NIVEL_3">Nível 3</option>
              </Select>
              <Notice kind="info">Sua ONG entra como “não verificada” e só participa de matches após a verificação da equipe.</Notice>
            </motion.div>
          )}
        </AnimatePresence>

        <label className="flex cursor-pointer items-start gap-3 text-sm">
          <input type="checkbox" checked={aceite} onChange={(e) => setAceite(e.target.checked)}
            className="mt-0.5 h-5 w-5 shrink-0 accent-[#e78b48]" />
          <span>
            Li e aceito os <Link href="/termos" target="_blank" className="font-semibold text-brick underline">Termos de Uso</Link> e a{" "}
            <Link href="/privacidade" target="_blank" className="font-semibold text-brick underline">Política de Privacidade</Link>.
          </span>
        </label>
        {erros.aceiteTermos && <Notice>{erros.aceiteTermos}</Notice>}

        {erro && <Notice>{erro}</Notice>}
        <Button type="submit" loading={loading} disabled={!cnpjOk || !aceite} className="mt-1 w-full !py-3.5">Criar conta</Button>
        <motion.span transition={spring} />
      </form>
    </AuthFrame>
  );
}

export default function RegistroPage() {
  return (
    <Suspense>
      <RegistroForm />
    </Suspense>
  );
}
