import { cookies } from "next/headers";
import { COOKIE, REFRESH_COOKIE, decodeToken } from "@/lib/jwt";

/**
 * Sessão no servidor (BFF). O navegador só tem cookies httpOnly: o access token (curto) e o refresh token
 * (rotativo). Quando o access vence, o BFF o troca sozinho; o usuário não percebe.
 */
export const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

interface Par {
  accessToken: string;
  expiresInSeconds: number;
  refreshToken: string;
  refreshExpiresInSeconds: number;
}

type Resultado = { ok: true; par: Par } | { ok: false };

// --- renovação "single-flight" + janela de tolerância ---------------------------------------------------
// O backend trata o REUSO de um refresh token como roubo e derruba a sessão. Num navegador, porém, várias
// requisições paralelas (e uma ou outra atrasada) carregam o mesmo cookie antigo. Para não confundir isso com
// um ataque: (1) renovações simultâneas do mesmo token compartilham UMA chamada ao backend e (2) por 30 s o
// resultado fica guardado para quem ainda chegar com o token antigo.
// Limite conhecido: o cache é por processo; com várias instâncias do BFF, use afinidade de sessão.
const emVoo = new Map<string, Promise<Resultado>>();
const recentes = new Map<string, { quando: number; resultado: Resultado }>();
const JANELA_MS = 30_000;

function limpar() {
  const agora = Date.now();
  for (const [k, v] of recentes) if (agora - v.quando > JANELA_MS) recentes.delete(k);
}

async function chamarRefresh(refresh: string, headers: Headers): Promise<Resultado> {
  try {
    const h = new Headers({ "Content-Type": "application/json" });
    for (const k of ["user-agent", "x-forwarded-for"]) {
      const v = headers.get(k);
      if (v) h.set(k, v);
    }
    const res = await fetch(`${BACKEND}/api/auth/refresh`, {
      method: "POST", headers: h, body: JSON.stringify({ refreshToken: refresh }), cache: "no-store",
    });
    if (!res.ok) return { ok: false };
    return { ok: true, par: (await res.json()) as Par };
  } catch {
    return { ok: false };
  }
}

async function renovarCompartilhado(refresh: string, headers: Headers): Promise<Resultado> {
  limpar();
  const recente = recentes.get(refresh);
  if (recente) return recente.resultado;
  let voo = emVoo.get(refresh);
  if (!voo) {
    voo = chamarRefresh(refresh, headers).then((r) => {
      if (r.ok) recentes.set(refresh, { quando: Date.now(), resultado: r });
      emVoo.delete(refresh);
      return r;
    });
    emVoo.set(refresh, voo);
  }
  return voo;
}

// --- cookies --------------------------------------------------------------------------------------------
const seguro = process.env.NODE_ENV === "production";

export async function gravarSessao(par: Par) {
  const jar = await cookies();
  // o access cookie vive tanto quanto o refresh para estar presente quando o JWT vence (o JWT carrega o próprio exp)
  jar.set(COOKIE, par.accessToken, {
    httpOnly: true, secure: seguro, sameSite: "lax", path: "/", maxAge: par.refreshExpiresInSeconds,
  });
  jar.set(REFRESH_COOKIE, par.refreshToken, {
    httpOnly: true, secure: seguro, sameSite: "lax", path: "/api", maxAge: par.refreshExpiresInSeconds,
  });
}

export async function apagarSessao() {
  const jar = await cookies();
  jar.delete(COOKIE);
  jar.set(REFRESH_COOKIE, "", { httpOnly: true, secure: seguro, sameSite: "lax", path: "/api", maxAge: 0 });
}

/**
 * Devolve um access token válido: o do cookie, se ainda tem folga, ou um novo obtido pelo refresh token.
 * Sem sessão utilizável, apaga os cookies e devolve undefined.
 */
export async function accessValido(headers: Headers, forcarRenovacao = false): Promise<string | undefined> {
  const jar = await cookies();
  const atual = jar.get(COOKIE)?.value;
  const info = decodeToken(atual);
  if (!forcarRenovacao && atual && info && info.exp * 1000 - Date.now() > 10_000) return atual;

  const refresh = jar.get(REFRESH_COOKIE)?.value;
  if (!refresh) {
    if (atual) await apagarSessao();
    return undefined;
  }
  const r = await renovarCompartilhado(refresh, headers);
  if (!r.ok) {
    await apagarSessao();
    return undefined;
  }
  await gravarSessao(r.par);
  return r.par.accessToken;
}
