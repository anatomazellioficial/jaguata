"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Suspense, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Field, Notice, Segmented, Select, spring } from "@/components/ui";
import { ApiError, api, login } from "@/lib/api";
import { homeFor } from "@/lib/jwt";
import type { PorteEmpresa } from "@/lib/types";

const digits = (v: string) => v.replace(/\D/g, "");

function RegistroForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [tipo, setTipo] = useState<"empresa" | "ong">(params.get("tipo") === "ong" ? "ong" : "empresa");
  const [f, setF] = useState({
    email: "", senha: "", cnpj: "", razaoSocial: "", cnaePrincipal: "", cep: "", lat: "", lng: "",
    porteReceita: "ME" as PorteEmpresa, nivelCapacidade: "NIVEL_1",
  });
  const [erros, setErros] = useState<Record<string, string>>({});
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF((s) => ({ ...s, [k]: e.target.value }));

  function geolocalizar() {
    navigator.geolocation?.getCurrentPosition((p) =>
      setF((s) => ({ ...s, lat: p.coords.latitude.toFixed(6), lng: p.coords.longitude.toFixed(6) })));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErro(undefined);
    setErros({});
    setLoading(true);
    const base = {
      cnpj: digits(f.cnpj), razaoSocial: f.razaoSocial, cnaePrincipal: digits(f.cnaePrincipal),
      cep: digits(f.cep), lat: Number(f.lat), lng: Number(f.lng),
    };
    try {
      if (tipo === "empresa") {
        await api("auth/registro/empresa", {
          method: "POST", body: { email: f.email, senha: f.senha, empresa: { ...base, porteReceita: f.porteReceita } },
        });
      } else {
        await api("auth/registro/ong", {
          method: "POST", body: { email: f.email, senha: f.senha, ong: { ...base, nivelCapacidade: f.nivelCapacidade } },
        });
      }
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

  // Erros de campos aninhados chegam como "empresa.cnpj" / "ong.cnpj"
  const e = (k: string) => erros[`${tipo}.${k}`] ?? erros[k];

  return (
    <AuthFrame
      title="Criar conta"
      subtitle="Leva menos de um minuto."
      footer={<>Já tem conta? <Link className="font-semibold text-brick hover:underline" href="/login">Entrar</Link></>}
    >
      <div className="mb-6 flex justify-center">
        <Segmented label="Tipo de conta" value={tipo} onChange={setTipo}
          options={[{ value: "empresa", label: "Sou empresa" }, { value: "ong", label: "Sou ONG" }]} />
      </div>

      <form onSubmit={submit} className="space-y-4">
        <Field label="E-mail" type="email" autoComplete="email" required value={f.email} onChange={set("email")} error={erros.email} />
        <Field label="Senha" type="password" autoComplete="new-password" required minLength={10} maxLength={72}
          hint="Mínimo de 10 caracteres. Evite senhas comuns e o seu e-mail." value={f.senha} onChange={set("senha")} error={erros.senha} />
        <Field label="CNPJ" inputMode="numeric" required placeholder="Somente números" value={f.cnpj} onChange={set("cnpj")} error={e("cnpj")} />
        <Field label="Razão social" required value={f.razaoSocial} onChange={set("razaoSocial")} error={e("razaoSocial")} />
        <Field label="CNAE principal" inputMode="numeric" required placeholder="Ex.: 6201501" value={f.cnaePrincipal} onChange={set("cnaePrincipal")} error={e("cnaePrincipal")} />
        <AnimatePresence mode="wait" initial={false}>
          <motion.div key={tipo} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.18 }}>
            {tipo === "empresa" ? (
              <Select label="Porte (faturamento)" value={f.porteReceita} onChange={set("porteReceita")}>
                <option value="MEI">MEI</option><option value="ME">ME</option>
                <option value="EPP">EPP</option><option value="DEMAIS">Demais</option>
              </Select>
            ) : (
              <Select label="Nível de capacidade" value={f.nivelCapacidade} onChange={set("nivelCapacidade")}>
                <option value="NIVEL_1">Nível 1</option><option value="NIVEL_2">Nível 2</option><option value="NIVEL_3">Nível 3</option>
              </Select>
            )}
          </motion.div>
        </AnimatePresence>
        <Field label="CEP" inputMode="numeric" required placeholder="Somente números" value={f.cep} onChange={set("cep")} error={e("cep")} />
        <div className="grid grid-cols-2 gap-3">
          <Field label="Latitude" required inputMode="decimal" value={f.lat} onChange={set("lat")} error={e("lat")} />
          <Field label="Longitude" required inputMode="decimal" value={f.lng} onChange={set("lng")} error={e("lng")} />
        </div>
        <motion.button type="button" onClick={geolocalizar} whileTap={{ scale: 0.96 }} transition={spring}
          className="rounded-full bg-navy/[.06] px-3.5 py-1.5 text-xs font-semibold text-navy hover:bg-navy/[.1]">
          📍 Usar minha localização
        </motion.button>
        {erro && <Notice>{erro}</Notice>}
        <AnimatePresence>
          {tipo === "ong" && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }} className="overflow-hidden">
              <Notice kind="info">Sua ONG entra como “não verificada” e só participa de matches após a verificação da equipe.</Notice>
            </motion.div>
          )}
        </AnimatePresence>
        <Button type="submit" loading={loading} className="mt-1 w-full !py-3.5">Criar conta</Button>
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
