/**
 * Defesas do BFF (rotas /api/proxy e /api/session). Só roda no servidor.
 */

/** Tamanho máximo de corpo aceito pelo BFF (o backend também limita). */
export const MAX_BODY = 64 * 1024;

/**
 * Defesa em profundidade contra CSRF (além do cookie SameSite=Lax): requisições que mudam estado
 * só valem se vierem da mesma origem. Navegadores sempre enviam Origin/Sec-Fetch-Site em POST/PATCH;
 * clientes sem navegador (curl) não têm cookies de sessão para serem abusados.
 */
export function sameOrigin(req: Request): boolean {
  const site = req.headers.get("sec-fetch-site");
  if (site && site !== "same-origin" && site !== "none") return false;
  const origin = req.headers.get("origin");
  if (!origin) return true;
  const host = req.headers.get("x-forwarded-host") ?? req.headers.get("host");
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

/** Lê o corpo com teto de tamanho. Retorna null se passar do limite. */
export async function readBody(req: Request): Promise<string | null> {
  const declared = Number(req.headers.get("content-length") ?? 0);
  if (declared > MAX_BODY) return null;
  const text = await req.text();
  return new TextEncoder().encode(text).length > MAX_BODY ? null : text;
}

/** Repassa a cadeia X-Forwarded-For recebida (quem a define é o proxy reverso confiável na frente do BFF). */
export function forwardClientIp(req: Request, headers: Headers) {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) headers.set("x-forwarded-for", xff.slice(0, 200));
}

/** Segmentos de caminho permitidos no proxy: sem "..", barras, "%", espaços ou controle. */
export function safeSegments(path: string[]): boolean {
  return path.length > 0 && path.length <= 6 && path.every((p) => /^[A-Za-z0-9_~-][A-Za-z0-9._~-]*$/.test(p));
}

export function forbidden(detail = "Origem não permitida.") {
  return Response.json({ detail }, { status: 403 });
}
