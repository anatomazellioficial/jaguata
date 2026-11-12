import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import { forbidden, forwardClientIp, readBody, safeSegments, sameOrigin } from "@/lib/guard";
import { COOKIE } from "@/lib/jwt";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Proxy para o backend: injeta o JWT do cookie httpOnly como Bearer.
 * - /auth/login é bloqueado aqui (usa /api/session/login) para que o token jamais
 *   seja devolvido ao JavaScript do navegador;
 * - métodos que mudam estado exigem mesma origem (anti-CSRF);
 * - caminho validado por allowlist de caracteres; corpo e query com teto de tamanho.
 */
async function handler(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;

  if (!safeSegments(path) || path.join("/") === "auth/login" || req.nextUrl.search.length > 512) {
    return NextResponse.json({ detail: "Rota não permitida." }, { status: 404 });
  }

  const muda = !["GET", "HEAD"].includes(req.method);
  if (muda && !sameOrigin(req)) return forbidden();

  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const token = (await cookies()).get(COOKIE)?.value;
  if (token) headers.set("authorization", `Bearer ${token}`);
  forwardClientIp(req, headers);

  let body: string | undefined;
  if (muda) {
    const lido = await readBody(req);
    if (lido === null) return NextResponse.json({ detail: "Corpo da requisição grande demais." }, { status: 413 });
    body = lido;
  }

  const res = await fetch(`${BACKEND}/api/${path.join("/")}${req.nextUrl.search}`, {
    method: req.method,
    headers,
    body,
    cache: "no-store",
  });

  const out = new NextResponse(res.status === 204 ? null : res.body, { status: res.status });
  const ct = res.headers.get("content-type");
  if (ct) out.headers.set("content-type", ct);
  const retry = res.headers.get("retry-after");
  if (retry) out.headers.set("retry-after", retry);
  out.headers.set("cache-control", "no-store"); // respostas autenticadas nunca em cache
  return out;
}

export { handler as GET, handler as POST, handler as PATCH, handler as PUT, handler as DELETE };
