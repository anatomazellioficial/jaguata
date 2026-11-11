import { NextResponse } from "next/server";
import { COOKIE, decodeToken } from "@/lib/jwt";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function POST(req: Request) {
  const body = await req.text();
  const res = await fetch(`${BACKEND}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
    cache: "no-store",
  });

  if (!res.ok) {
    const problem = await res.json().catch(() => ({ detail: "Falha ao autenticar." }));
    return NextResponse.json(problem, { status: res.status });
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
  return out;
}
