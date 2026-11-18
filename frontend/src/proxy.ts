import { NextRequest, NextResponse } from "next/server";
import { COOKIE, decodeToken, homeFor, type Papel } from "@/lib/jwt";

/** Cada área só aceita o seu papel. Guarda de UX: a autorização real é feita pelo backend. */
const AREAS: Record<string, Papel> = { "/empresa": "EMPRESA", "/ong": "ONG", "/admin": "ADMIN" };
const PRIVADAS = ["/empresa", "/ong", "/admin", "/conta", "/aceitar-termos"];

const isProd = process.env.NODE_ENV === "production";

/**
 * CSP com nonce por requisição: só roda script que o servidor marcou nesta resposta ('strict-dynamic' propaga a
 * confiança aos chunks que o Next carrega). Estilos: elementos <style> exigem o nonce; atributos style="" (usados
 * pelas animações) ficam permitidos só em style-src-attr, que não executa código.
 */
function csp(nonce: string) {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isProd ? "" : " 'unsafe-eval'"}`,
    `style-src 'self' 'nonce-${nonce}'`,
    "style-src-attr 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self' data:",
    "connect-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join("; ");
}

/**
 * Modo "só demonstração" (DEMO_ONLY=1, usado na hospedagem do protótipo, que não tem backend): tudo que não for o
 * protótipo, um arquivo estático ou um ícone/manifesto redireciona para /demo.
 */
const somenteDemo = () => process.env.DEMO_ONLY === "1";

export function proxy(req: NextRequest) {
  const { pathname } = req.nextUrl;

  if (somenteDemo() && pathname !== "/demo" && !pathname.startsWith("/demo/") && !/\.[a-z0-9]+$/i.test(pathname)) {
    return NextResponse.redirect(new URL("/demo", req.url));
  }

  if (PRIVADAS.some((a) => pathname === a || pathname.startsWith(a + "/"))) {
    // access token vencido ainda indica o papel; o BFF o renova na primeira chamada à API.
    // Sem nenhum dos dois cookies, não há sessão: login.
    const info = decodeToken(req.cookies.get(COOKIE)?.value, true);
    if (!info) {
      const url = new URL("/login", req.url);
      url.searchParams.set("next", pathname);
      return NextResponse.redirect(url);
    }
    const area = Object.keys(AREAS).find((a) => pathname === a || pathname.startsWith(a + "/"));
    if (area && AREAS[area] !== info.papel) {
      return NextResponse.redirect(new URL(homeFor(info.papel), req.url));
    }
  }

  const nonce = btoa(crypto.randomUUID());
  const politica = csp(nonce);
  const headers = new Headers(req.headers);
  headers.set("x-nonce", nonce);
  headers.set("Content-Security-Policy", politica);   // o Next lê o nonce daqui e o aplica aos seus scripts
  const res = NextResponse.next({ request: { headers } });
  res.headers.set("Content-Security-Policy", politica);
  return res;
}

// todas as páginas; fora: estáticos, imagens e as rotas /api (JSON, sem documento para proteger)
export const config = {
  matcher: [{
    source: "/((?!api|_next/static|_next/image|favicon.ico).*)",
    missing: [{ type: "header", key: "next-router-prefetch" }, { type: "header", key: "purpose", value: "prefetch" }],
  }],
};
