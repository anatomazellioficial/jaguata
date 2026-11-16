import { NextResponse } from "next/server";
import { gravarSessao } from "@/lib/auth-server";
import { BACKEND } from "@/lib/auth-server";
import { forbidden, forwardClientIp, readBody, sameOrigin } from "@/lib/guard";
import { decodeToken } from "@/lib/jwt";

export async function POST(req: Request) {
  if (!sameOrigin(req)) return forbidden();
  const body = await readBody(req);
  if (body === null) return NextResponse.json({ detail: "Corpo da requisição grande demais." }, { status: 413 });

  const headers = new Headers({ "Content-Type": "application/json" });
  forwardClientIp(req, headers); // o backend limita tentativas por IP
  const ua = req.headers.get("user-agent");
  if (ua) headers.set("user-agent", ua);

  const res = await fetch(`${BACKEND}/api/auth/login`, { method: "POST", headers, body, cache: "no-store" });

  if (!res.ok) {
    // repassa o problema (inclui "codigo": "EMAIL_NAO_VERIFICADO" para o frontend oferecer o reenvio)
    const problem = await res.json().catch(() => ({ detail: "Falha ao autenticar." }));
    const out = NextResponse.json(problem, { status: res.status });
    const retry = res.headers.get("retry-after");
    if (retry) out.headers.set("retry-after", retry);
    out.headers.set("cache-control", "no-store");
    return out;
  }

  const par = await res.json();
  const info = decodeToken(par.accessToken);
  const out = NextResponse.json({ papel: info?.papel, empresaId: info?.empresaId, ongId: info?.ongId });
  // Os tokens vivem só em cookies httpOnly: o JavaScript do navegador nunca os enxerga.
  await gravarSessao(par);
  out.headers.set("cache-control", "no-store");
  return out;
}
