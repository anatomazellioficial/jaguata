import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { BACKEND, apagarSessao } from "@/lib/auth-server";
import { forbidden, sameOrigin } from "@/lib/guard";
import { REFRESH_COOKIE } from "@/lib/jwt";

/** Encerra a sessão no servidor (revoga o refresh token) e apaga os cookies. Idempotente. */
export async function POST(req: Request) {
  if (!sameOrigin(req)) return forbidden();
  const refresh = (await cookies()).get(REFRESH_COOKIE)?.value;
  if (refresh) {
    try {
      await fetch(`${BACKEND}/api/auth/logout`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: refresh }), cache: "no-store",
      });
    } catch {
      /* sem backend, o logout local acontece mesmo assim */
    }
  }
  await apagarSessao();
  const out = NextResponse.json({ ok: true });
  out.headers.set("cache-control", "no-store");
  return out;
}
