import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import { COOKIE } from "@/lib/jwt";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Proxy para o backend: injeta o JWT do cookie httpOnly como Bearer.
 * /auth/login é bloqueado aqui (usa /api/session/login) para que o token jamais
 * seja devolvido ao JavaScript do navegador.
 */
async function handler(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const target = path.join("/");

  if (target === "auth/login" || path.some((p) => p === ".." || p === ".")) {
    return NextResponse.json({ detail: "Rota não permitida." }, { status: 404 });
  }

  const headers = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const token = (await cookies()).get(COOKIE)?.value;
  if (token) headers.set("authorization", `Bearer ${token}`);

  const hasBody = !["GET", "HEAD"].includes(req.method);
  const res = await fetch(`${BACKEND}/api/${target}${req.nextUrl.search}`, {
    method: req.method,
    headers,
    body: hasBody ? await req.text() : undefined,
    cache: "no-store",
  });

  const out = new NextResponse(res.status === 204 ? null : res.body, { status: res.status });
  const ct = res.headers.get("content-type");
  if (ct) out.headers.set("content-type", ct);
  return out;
}

export { handler as GET, handler as POST, handler as PATCH, handler as PUT, handler as DELETE };
