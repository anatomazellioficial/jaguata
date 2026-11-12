"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Suspense, useState } from "react";
import { CnpjField } from "@/components/Lookups";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice, Segmented, Select } from "@/components/ui";
import { ApiError, api, login } from "@/lib/api";
import { homeFor } from "@/lib/jwt";

function RegistroForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [tipo, setTipo] = useState<"empresa" | "ong">(params.get("tipo") === "ong" ? "ong" : "empresa");
  const [f, setF] = useState({ email: "", senha: "", cnpj: "", nivelCapacidade: "NIVEL_1" });
  const [cnpjOk, setCnpjOk] = useState(false);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

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
        body: { email: f.email, senha: f.senha, cnpj: f.cnpj, ...(tipo === "ong" ? { nivelCapacidade: f.nivelCapacidade } : {}) },
      });
      const s = await login(f.email, f.senha);
      router.push(homeFor(s.papel));
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        setErro(err.message);
        setErros(err.campos ?? {});
      } else setErro("Não foi possível concluir o cadastro.");
      setLoading(false);
    }
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

        {erro && <Notice>{erro}</Notice>}
        <Button type="submit" loading={loading} disabled={!cnpjOk} className="mt-1 w-full !py-3.5">Criar conta</Button>
        {!cnpjOk && f.cnpj.length === 14 ? null : null}
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
