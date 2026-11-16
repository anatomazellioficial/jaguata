"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { AuthFrame } from "@/components/Shell";
import { Button, Notice } from "@/components/ui";
import { ApiError, api, getSession } from "@/lib/api";
import { homeFor } from "@/lib/jwt";
import { VERSAO_TERMOS } from "@/lib/types";

function Aceite() {
  const router = useRouter();
  const params = useSearchParams();
  const [ok, setOk] = useState(false);
  const [erro, setErro] = useState<string>();
  const [loading, setLoading] = useState(false);

  async function aceitar() {
    setLoading(true); setErro(undefined);
    try {
      await api("conta/termos/aceitar", { method: "POST" });
      const next = params.get("next");
      const destino = next && next.startsWith("/") && !next.startsWith("//") ? next : homeFor((await getSession()).papel);
      router.push(destino);
      router.refresh();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Não foi possível registrar o aceite.");
      setLoading(false);
    }
  }

  return (
    <AuthFrame
      title="Termos atualizados"
      subtitle={`Atualizamos os Termos de Uso e a Política de Privacidade (versão ${VERSAO_TERMOS}). Leia e aceite para continuar.`}
      footer={<Link href="/login" className="font-semibold text-brick hover:underline">Sair</Link>}
    >
      <div className="space-y-4">
        <p className="text-sm">
          <Link href="/termos" target="_blank" className="font-semibold text-brick underline">Termos de Uso</Link>{" · "}
          <Link href="/privacidade" target="_blank" className="font-semibold text-brick underline">Política de Privacidade</Link>
        </p>
        <label className="flex cursor-pointer items-start gap-3 rounded-2xl bg-navy/[.04] p-4 text-sm">
          <input type="checkbox" checked={ok} onChange={(e) => setOk(e.target.checked)} className="mt-0.5 h-5 w-5 shrink-0 accent-[#e78b48]" />
          <span>Li e aceito os Termos de Uso e a Política de Privacidade atualizados.</span>
        </label>
        {erro && <Notice>{erro}</Notice>}
        <Button onClick={aceitar} loading={loading} disabled={!ok} className="w-full !py-3.5">Aceitar e continuar</Button>
      </div>
    </AuthFrame>
  );
}

export default function AceitarTermosPage() {
  return (
    <Suspense>
      <Aceite />
    </Suspense>
  );
}
