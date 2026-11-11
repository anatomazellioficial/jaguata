import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { COOKIE, decodeToken } from "@/lib/jwt";

export async function GET() {
  const info = decodeToken((await cookies()).get(COOKIE)?.value);
  if (!info) return NextResponse.json({ detail: "Não autenticado." }, { status: 401 });
  return NextResponse.json({ papel: info.papel, empresaId: info.empresaId, ongId: info.ongId });
}
