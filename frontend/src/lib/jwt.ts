// Decodificação SEM verificação de assinatura: serve só para roteamento/UX.
// Quem garante a segurança é o backend, que valida o token em toda requisição.

export type Papel = "EMPRESA" | "ONG" | "ADMIN";

export interface SessionInfo {
  papel: Papel;
  empresaId?: string;
  ongId?: string;
  exp: number;
}

/** Access token (JWT curto). Cookie httpOnly, vive tanto quanto a sessão para ainda estar lá quando o JWT expira. */
export const COOKIE = "jaguata_token";
/** Refresh token opaco e rotativo. httpOnly, restrito a /api (só os handlers do BFF o recebem). */
export const REFRESH_COOKIE = "jaguata_refresh";

/**
 * @param ignorarExpiracao usado pela guarda de rotas: um access token vencido ainda indica o papel do usuário
 * e o BFF o renova de forma transparente na próxima chamada.
 */
export function decodeToken(token: string | undefined, ignorarExpiracao = false): SessionInfo | null {
  if (!token) return null;
  try {
    const part = token.split(".")[1];
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), "="))
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    const p = JSON.parse(json);
    if (!p.papel || typeof p.exp !== "number") return null;
    if (!ignorarExpiracao && p.exp * 1000 < Date.now()) return null;
    return { papel: p.papel, empresaId: p.empresaId, ongId: p.ongId, exp: p.exp };
  } catch {
    return null;
  }
}

export function homeFor(papel: Papel): string {
  return papel === "ADMIN" ? "/admin" : papel === "ONG" ? "/ong" : "/empresa";
}
