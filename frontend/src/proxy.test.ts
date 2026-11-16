// @vitest-environment node
import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "./proxy";

const jwt = (papel: string) => {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "HS256" })}.${b64({ papel, exp: Math.floor(Date.now() / 1000) + 600 })}.x`;
};
const req = (path: string, token?: string) =>
  new NextRequest(`http://localhost:3000${path}`, token ? { headers: { cookie: `jaguata_token=${token}` } } : undefined);

describe("CSP com nonce", () => {
  it("cada resposta traz um nonce novo, sem unsafe-inline em scripts", () => {
    const a = proxy(req("/login"));
    const b = proxy(req("/login"));
    const cspA = a.headers.get("content-security-policy")!;
    const cspB = b.headers.get("content-security-policy")!;
    const nonce = (c: string) => /script-src[^;]*'nonce-([^']+)'/.exec(c)![1];
    expect(nonce(cspA)).not.toBe(nonce(cspB));
    expect(cspA).toMatch(/script-src[^;]*'strict-dynamic'/);
    expect(/script-src[^;]*'unsafe-inline'/.test(cspA)).toBe(false);
    expect(cspA).toContain("object-src 'none'");
    expect(cspA).toContain("frame-ancestors 'none'");
  });

  it("estilos de elemento exigem nonce; só atributos style ficam liberados", () => {
    const c = proxy(req("/")).headers.get("content-security-policy")!;
    expect(/style-src 'self' 'nonce-[^']+'/.test(c)).toBe(true);
    expect(c).toContain("style-src-attr 'unsafe-inline'");
  });

  it("repassa o nonce à renderização pelo cabeçalho da requisição", () => {
    const res = proxy(req("/login"));
    expect(res.headers.get("x-middleware-request-x-nonce")).toBeTruthy();
  });
});

describe("guarda de áreas", () => {
  it("sem sessão, área privada vai para o login com next", () => {
    const res = proxy(req("/empresa"));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toContain("/login?next=%2Fempresa");
  });

  it("cada papel só entra na sua área", () => {
    const res = proxy(req("/admin", jwt("EMPRESA")));
    expect(res.headers.get("location")).toContain("/empresa");
    expect(proxy(req("/empresa", jwt("EMPRESA"))).status).toBe(200);
  });

  it("a tela de aceite de termos exige sessão", () => {
    expect(proxy(req("/aceitar-termos")).status).toBe(307);
    expect(proxy(req("/aceitar-termos", jwt("ONG"))).status).toBe(200);
  });

  it("páginas públicas não exigem sessão", () => {
    expect(proxy(req("/login")).status).toBe(200);
    expect(proxy(req("/termos")).status).toBe(200);
  });
});
