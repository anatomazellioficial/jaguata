import { NextResponse } from "next/server";
import { forbidden, sameOrigin } from "@/lib/guard";
import { COOKIE } from "@/lib/jwt";

export async function POST(req: Request) {
  if (!sameOrigin(req)) return forbidden();
  const out = NextResponse.json({ ok: true });
  out.cookies.delete(COOKIE);
  out.headers.set("cache-control", "no-store");
  return out;
}
