import { expect, type APIRequestContext, type Page } from "@playwright/test";

/** Utilitários compartilhados pelas jornadas ponta a ponta (cadastro por API, e-mails no Mailpit, login pela tela). */
export const API = process.env.E2E_API ?? "http://127.0.0.1:8180";
export const MAILPIT = process.env.E2E_MAILPIT ?? "http://127.0.0.1:8026";
export const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@jaguata.local";
export const ADMIN_SENHA = process.env.ADMIN_PASSWORD ?? "girassol-azul-dev-42";
export const SENHA = "senha-e2e-segura-2026";
export const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? "jaguata-db-1";

/** CNPJ válido (dígitos verificadores corretos) com o prefixo dado e o resto aleatório. */
export function cnpjValido(prefixo: string): string {
  const base = prefixo + String(Math.floor(Math.random() * 1e6)).padStart(6, "0") + "0001";
  const dv = (digs: string) => {
    const pesos = digs.length === 12 ? [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2] : [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
    const soma = [...digs].reduce((s, d, i) => s + Number(d) * pesos[i], 0);
    const r = soma % 11;
    return r < 2 ? 0 : 11 - r;
  };
  const d1 = dv(base);
  return base + d1 + dv(base + d1);
}

export interface Mensagem { assunto: string; texto: string }

/** Mensagens recebidas por um endereço (Mailpit). */
export async function emailsPara(request: APIRequestContext, email: string): Promise<Mensagem[]> {
  const r = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent("to:" + email)}`);
  const msgs = (await r.json()).messages ?? [];
  const out: Mensagem[] = [];
  for (const m of msgs) {
    const det = await (await request.get(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
    out.push({ assunto: det.Subject ?? "", texto: det.Text ?? "" });
  }
  return out;
}

export async function tokenDoEmail(request: APIRequestContext, email: string): Promise<string> {
  for (let i = 0; i < 30; i++) {
    for (const m of await emailsPara(request, email)) {
      const t = /verificar-email\?token=([\w-]+)/.exec(m.texto)?.[1];
      if (t) return t;
    }
    await new Promise((res) => setTimeout(res, 500));
  }
  throw new Error("e-mail de verificação não chegou: " + email);
}

/** Espera chegar (ou não) um e-mail cujo assunto contenha o trecho. */
export async function aguardarEmail(request: APIRequestContext, email: string, trecho: string, timeoutMs = 15_000) {
  const limite = Date.now() + timeoutMs;
  while (Date.now() < limite) {
    const achado = (await emailsPara(request, email)).find((m) => m.assunto.includes(trecho));
    if (achado) return achado;
    await new Promise((res) => setTimeout(res, 500));
  }
  throw new Error(`e-mail "${trecho}" não chegou para ${email}`);
}

export async function registrar(request: APIRequestContext, tipo: "ong" | "empresa", email: string) {
  const corpo: Record<string, unknown> = {
    email, senha: SENHA, aceiteTermos: true, cnpj: cnpjValido(tipo === "ong" ? "55" : "11"),
  };
  if (tipo === "ong") corpo.nivelCapacidade = "NIVEL_1";
  const r = await request.post(`${API}/api/auth/registro/${tipo}`, { data: corpo });
  expect(r.status(), await r.text()).toBe(201);
  const entidadeId = (await r.json()).entidadeId as string;
  const t = await tokenDoEmail(request, email);
  expect((await request.post(`${API}/api/auth/email/verificar`, { data: { token: t } })).status()).toBe(204);
  return entidadeId;
}

export async function tokenApi(request: APIRequestContext, email: string, senha = SENHA): Promise<string> {
  const r = await request.post(`${API}/api/auth/login`, { data: { email, senha } });
  expect(r.status(), await r.text()).toBe(200);
  return (await r.json()).accessToken as string;
}

/** Cadastra uma ONG e a verifica como administrador. */
export async function ongVerificada(request: APIRequestContext, email: string) {
  const id = await registrar(request, "ong", email);
  const adm = await tokenApi(request, ADMIN_EMAIL, ADMIN_SENHA);
  const v = await request.post(`${API}/api/ongs/${id}/verificar`, { headers: { Authorization: `Bearer ${adm}` } });
  expect(v.status()).toBe(200);
  return id;
}

export const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL(/\/(ong|empresa|aceitar-termos)/);
}
