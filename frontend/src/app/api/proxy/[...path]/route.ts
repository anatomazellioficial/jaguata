import { NextRequest, NextResponse } from "next/server";
import { BACKEND, accessValido, apagarSessao } from "@/lib/auth-server";
import { forbidden, forwardClientIp, readBody, safeSegments, sameOrigin } from "@/lib/guard";

/** Rotas de auth que NUNCA passam pelo proxy: devolvem tokens ou mexem na sessão e têm handlers próprios. */
const BLOQUEADAS = ["auth/login", "auth/refresh", "auth/logout"];

/**
 * Proxy para o backend: injeta o access token (renovado se preciso) como Bearer.
 * - login/refresh/logout são bloqueados aqui (têm handlers em /api/session) para que token algum
 *   volte ao JavaScript do navegador;
 * - métodos que mudam estado exigem mesma origem (anti-CSRF);
 * - caminho validado por allowlist de caracteres; corpo e query com teto de tamanho;
 * - se o backend recusar o access (401), tenta UMA renovação e repete a chamada.
 */
async function handler(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const alvo = path.join("/");

  if (!safeSegments(path) || BLOQUEADAS.includes(alvo) || req.nextUrl.search.length > 512) {
    return NextResponse.json({ detail: "Rota não permitida." }, { status: 404 });
  }

  const muda = !["GET", "HEAD"].includes(req.method);
  if (muda && !sameOrigin(req)) return forbidden();

  let corpo: string | undefined;
  if (muda) {
    const lido = await readBody(req);
    if (lido === null) return NextResponse.json({ detail: "Corpo da requisição grande demais." }, { status: 413 });
    corpo = lido;
  }

  const chamar = async (token: string | undefined) => {
    const headers = new Headers();
    const contentType = req.headers.get("content-type");
    if (contentType) headers.set("content-type", contentType);
    if (token) headers.set("authorization", `Bearer ${token}`);
    const ua = req.headers.get("user-agent");
    if (ua) headers.set("user-agent", ua);
    forwardClientIp(req, headers);
    return fetch(`${BACKEND}/api/${alvo}${req.nextUrl.search}`, {
      method: req.method, headers, body: corpo, cache: "no-store",
    });
  };

  // rotas públicas (consulta, cadastro, e-mail, senha) funcionam sem sessão; as demais usam o access token
  let token = await accessValido(req.headers);
  let res = await chamar(token);
  if (res.status === 401 && token) {
    token = await accessValido(req.headers, true);
    if (token) res = await chamar(token);
    else await apagarSessao();
  }

  const out = new NextResponse(res.status === 204 ? null : res.body, { status: res.status });
  const ct = res.headers.get("content-type");
  if (ct) out.headers.set("content-type", ct);
  const retry = res.headers.get("retry-after");
  if (retry) out.headers.set("retry-after", retry);
  out.headers.set("cache-control", "no-store"); // respostas autenticadas nunca em cache
  return out;
}

export { handler as GET, handler as POST, handler as PATCH, handler as PUT, handler as DELETE };
