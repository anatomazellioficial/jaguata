import { NextRequest, NextResponse } from "next/server";
import { COOKIE, decodeToken, homeFor, type Papel } from "@/lib/jwt";

/** Cada área só aceita o seu papel. Guarda de UX: a autorização real é feita pelo backend. */
const AREAS: Record<string, Papel> = { "/empresa": "EMPRESA", "/ong": "ONG", "/admin": "ADMIN" };

export function proxy(req: NextRequest) {
  const info = decodeToken(req.cookies.get(COOKIE)?.value);
  const { pathname } = req.nextUrl;

  if (!info) {
    const url = new URL("/login", req.url);
    url.searchParams.set("next", pathname);
    return NextResponse.redirect(url);
  }

  const area = Object.keys(AREAS).find((a) => pathname === a || pathname.startsWith(a + "/"));
  if (area && AREAS[area] !== info.papel) {
    return NextResponse.redirect(new URL(homeFor(info.papel), req.url));
  }
  return NextResponse.next();
}

export const config = { matcher: ["/empresa/:path*", "/ong/:path*", "/admin/:path*"] };
