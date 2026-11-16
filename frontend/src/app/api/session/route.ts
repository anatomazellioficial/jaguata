import { NextResponse } from "next/server";
import { accessValido } from "@/lib/auth-server";
import { decodeToken } from "@/lib/jwt";

/** Quem sou eu? Renova a sessão de forma transparente se o access token venceu. */
export async function GET(req: Request) {
  const token = await accessValido(req.headers);
  const info = decodeToken(token);
  const res = info
    ? NextResponse.json({ papel: info.papel, empresaId: info.empresaId, ongId: info.ongId })
    : NextResponse.json({ detail: "Não autenticado." }, { status: 401 });
  res.headers.set("cache-control", "no-store");
  return res;
}
