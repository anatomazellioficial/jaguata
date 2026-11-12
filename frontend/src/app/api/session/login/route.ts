import { NextResponse } from "next/server";
import { forbidden, forwardClientIp, readBody, sameOrigin } from "@/lib/guard";
import { COOKIE, decodeToken } from "@/lib/jwt";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function POST(req: Request) {
  if (!sameOrigin(req)) return forbidden();
  const body = await readBody(req);
  if (body === null) return NextResponse.json({ detail: "Corpo da requisição grande demais." }, { status: 413 });

  const headers = new Headers({ "Content-Type": "application/json" });
  forwardClientIp(req, headers); // o backend limita tentativas por IP

  const res = await fetch(`${BACKEND}/api/auth/login`, { method: "POST", headers, body, cache: "no-store" });

  if (!res.ok) {
    const problem = await res.json().catch(() => ({ detail: "Falha ao autenticar." }));
    const out = NextResponse.json(problem, { status: res.status });
    const retry = res.headers.get("retry-after");
    if (retry) out.headers.set("retry-after", retry);
    out.headers.set("cache-control", "no-store");
    return out;
  }

  const { accessToken, expiresInSeconds } = await res.json();
  const info = decodeToken(accessToken);
  const out = NextResponse.json({ papel: info?.papel, empresaId: info?.empresaId, ongId: info?.ongId });
  // O token vive só em cookie httpOnly: o JavaScript do navegador nunca o enxerga.
  out.cookies.set(COOKIE, accessToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: expiresInSeconds,
  });
  out.headers.set("cache-control", "no-store");
  return out;
}
