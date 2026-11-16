import { describe, expect, it } from "vitest";
import { decodeToken, homeFor } from "./jwt";

function jwt(payload: object) {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "HS256" })}.${b64(payload)}.assinatura`;
}

describe("decodeToken", () => {
  const futuro = Math.floor(Date.now() / 1000) + 600;

  it("lê papel e entidade de um token válido", () => {
    const info = decodeToken(jwt({ papel: "ONG", ongId: "o1", exp: futuro }));
    expect(info).toMatchObject({ papel: "ONG", ongId: "o1" });
  });

  it("recusa token vencido, exceto quando a guarda de rotas ignora a expiração", () => {
    const vencido = jwt({ papel: "EMPRESA", exp: Math.floor(Date.now() / 1000) - 5 });
    expect(decodeToken(vencido)).toBeNull();
    expect(decodeToken(vencido, true)?.papel).toBe("EMPRESA");
  });

  it("recusa lixo, ausência e token sem papel", () => {
    expect(decodeToken(undefined)).toBeNull();
    expect(decodeToken("nao-e-jwt")).toBeNull();
    expect(decodeToken(jwt({ exp: futuro }))).toBeNull();
  });
});

describe("homeFor", () => {
  it("leva cada papel à sua área", () => {
    expect(homeFor("ADMIN")).toBe("/admin");
    expect(homeFor("ONG")).toBe("/ong");
    expect(homeFor("EMPRESA")).toBe("/empresa");
  });
});
