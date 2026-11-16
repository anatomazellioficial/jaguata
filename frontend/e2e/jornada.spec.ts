import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Jornada completa no navegador: ONG verificada cadastra e edita candidato e escolhe áreas de atuação; empresa
 * cria vaga com requisitos, busca com filtros, vê o ranking e "Por quê?", edita e exclui a vaga; conta com
 * avisos por e-mail e "Sair"; menu inferior no celular.
 */
const API = process.env.E2E_API ?? "http://127.0.0.1:8180";
const MAILPIT = process.env.E2E_MAILPIT ?? "http://127.0.0.1:8026";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@jaguata.local";
const ADMIN_SENHA = process.env.ADMIN_PASSWORD ?? "girassol-azul-dev-42";
const SENHA = "senha-e2e-segura-2026";

/** CNPJ válido (dígitos verificadores corretos) com o prefixo dado e o resto aleatório. */
function cnpjValido(prefixo: string): string {
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

async function tokenDoEmail(request: APIRequestContext, email: string): Promise<string> {
  for (let i = 0; i < 30; i++) {
    const r = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent("to:" + email)}`);
    const msgs = (await r.json()).messages ?? [];
    for (const m of msgs) {
      const det = await (await request.get(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
      const t = /verificar-email\?token=([\w-]+)/.exec(det.Text ?? "")?.[1];
      if (t) return t;
    }
    await new Promise((res) => setTimeout(res, 500));
  }
  throw new Error("e-mail de verificação não chegou: " + email);
}

async function registrar(request: APIRequestContext, tipo: "ong" | "empresa", email: string) {
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

async function entrar(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.waitForURL(/\/(ong|empresa)/);
}

const sufixo = Date.now().toString(36);
const emailOng = `ong.${sufixo}@e2e.local`;
const emailEmpresa = `empresa.${sufixo}@e2e.local`;
const nomeCandidato = `Maria E2E ${sufixo}`;

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  const ongId = await registrar(request, "ong", emailOng);
  await registrar(request, "empresa", emailEmpresa);
  const adm = await request.post(`${API}/api/auth/login`, { data: { email: ADMIN_EMAIL, senha: ADMIN_SENHA } });
  expect(adm.status(), "login do admin (defina ADMIN_EMAIL/ADMIN_PASSWORD)").toBe(200);
  const token = (await adm.json()).accessToken as string;
  const v = await request.post(`${API}/api/ongs/${ongId}/verificar`, { headers: { Authorization: `Bearer ${token}` } });
  expect(v.status()).toBe(200);
});

test("ONG: áreas de atuação, cadastro e edição de candidato", async ({ page }) => {
  await entrar(page, emailOng);

  // áreas de atuação
  await page.getByRole("tab", { name: "Áreas de atuação" }).click();
  await expect(page.getByText("Você ainda não escolheu áreas")).toBeVisible();
  await page.getByRole("button", { name: "Tecnologia e comunicação" }).click();
  await page.getByRole("button", { name: "Salvar áreas" }).click();
  await expect(page.getByText("Áreas salvas.")).toBeVisible();
  await page.screenshot({ path: "e2e/telas/ong-areas.png" });

  // novo candidato com o perfil novo
  await page.getByRole("button", { name: /Novo candidato/ }).first().click();
  const dlg = page.getByRole("dialog", { name: "Novo candidato" });
  await dlg.getByLabel("Nome completo").fill(nomeCandidato);
  await dlg.getByLabel("Data de nascimento").fill("1990-05-10");
  await dlg.getByLabel("CEP do candidato").fill("13015904");
  await dlg.getByLabel("Escolaridade").selectOption({ label: "Médio completo" });
  await dlg.getByRole("button", { name: "CLT", exact: true }).click();
  await dlg.locator("summary", { hasText: "Autodeclaração" }).click();
  await dlg.getByLabel("Gênero").selectOption({ label: "Mulher" });
  await dlg.getByLabel("Categoria da tag").selectOption("SKILL");
  await dlg.getByLabel("Tag", { exact: true }).fill("excel");
  await dlg.getByLabel("Tag", { exact: true }).press("Enter");
  await expect(dlg.getByRole("button", { name: "excel ✕" })).toBeVisible();
  await dlg.getByRole("checkbox").last().check();   // consentimento
  await page.screenshot({ path: "e2e/telas/ong-novo-candidato.png" });
  await dlg.getByRole("button", { name: "Cadastrar candidato" }).click();
  await expect(dlg).toBeHidden();

  // lista e edição
  await page.getByRole("tab", { name: "Meus candidatos" }).click();
  await expect(page.getByText(nomeCandidato)).toBeVisible();
  await page.getByRole("button", { name: "Editar" }).first().click();
  const ed = page.getByRole("dialog", { name: "Editar candidato" });
  await expect(ed.getByLabel("Nome completo")).toHaveValue(nomeCandidato);
  await ed.getByLabel("Nome completo").fill(nomeCandidato + " Silva");
  await ed.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(ed).toBeHidden();
  await expect(page.getByText(nomeCandidato + " Silva")).toBeVisible();
  await page.screenshot({ path: "e2e/telas/ong-candidatos.png" });
});

test("Empresa: vaga com requisitos, busca com filtros, ranking, Por quê?, edição e exclusão", async ({ page }) => {
  await entrar(page, emailEmpresa);

  await page.getByRole("button", { name: /Nova vaga/ }).first().click();
  const dlg = page.getByRole("dialog", { name: "Nova vaga" });
  await dlg.getByLabel("CEP de atuação").fill("13015904");
  await dlg.getByLabel("Escolaridade mínima").selectOption({ label: "Médio completo" });
  await dlg.getByLabel("Regime de contratação").selectOption({ label: "CLT" });
  await dlg.getByLabel("Habilidade").fill("Excel");
  await dlg.getByLabel("Habilidade").press("Enter");
  await dlg.getByText("Vaga afirmativa").click();
  await dlg.getByRole("button", { name: "Mulheres" }).click();
  await dlg.getByLabel(/Justificativa/).fill("Programa de equidade de gênero da empresa");
  await page.screenshot({ path: "e2e/telas/empresa-nova-vaga.png" });
  await dlg.getByRole("button", { name: "Publicar vaga" }).click();
  await expect(dlg).toBeHidden();

  const card = page.locator("article").first();
  await expect(card.getByText("Vaga afirmativa")).toBeVisible();
  await expect(card.getByText("CLT", { exact: true })).toBeVisible();

  // filtros + busca
  await card.getByRole("button", { name: "Filtros da busca" }).click();
  await card.getByLabel("Habilidades obrigatórias").fill("excel");
  await card.getByRole("button", { name: "Buscar candidatos" }).click();
  await expect(card.getByText(nomeCandidato + " Silva")).toBeVisible();
  await expect(card.getByText(/^#\d+$/).first()).toBeVisible();       // posição no ranking, sem nota numérica
  await card.getByRole("button", { name: "Por quê?" }).first().click();
  await expect(card.getByText("Proximidade")).toBeVisible();
  await expect(card.getByText("Habilidades", { exact: true })).toBeVisible();
  await expect(card.getByText("Vaga afirmativa", { exact: true })).toHaveCount(1);   // só o selo da vaga; o critério de prioridade fica oculto
  await page.screenshot({ path: "e2e/telas/empresa-ranking.png" });

  // a empresa não pode editar com candidato enviado, mas aqui só há sugestão: edita
  await card.getByRole("button", { name: "Editar" }).click();
  const ed = page.getByRole("dialog", { name: "Editar vaga" });
  await ed.getByLabel("Modelo de trabalho").selectOption({ label: "Híbrido" });
  await ed.getByRole("button", { name: "Salvar alterações" }).click();
  await expect(ed).toBeHidden();
  await expect(page.locator("article").first().locator("span", { hasText: /^Híbrido$/ })).toBeVisible();

  // exclusão com confirmação
  await page.locator("article").first().getByRole("button", { name: "Excluir" }).click();
  await page.locator("article").first().getByRole("button", { name: "Confirmar" }).click();
  await expect(page.getByText("Publique a sua primeira vaga")).toBeVisible();
});

test("Conta: avisos por e-mail e cartão Sair", async ({ page }) => {
  await entrar(page, emailEmpresa);
  await page.goto("/conta");
  const sw = page.getByRole("switch", { name: "Avisos por e-mail" });
  await expect(sw).toBeChecked();
  await sw.click();
  await expect(page.getByText("Desligados")).toBeVisible();
  await page.screenshot({ path: "e2e/telas/conta.png", fullPage: true });
  await expect(page.getByRole("heading", { name: "Sair" })).toBeVisible();
  await page.getByRole("heading", { name: "Sair" }).locator("..").locator("..").getByRole("button", { name: "Sair" }).click();
  await page.waitForURL(/\/login|\/$/);
});

test("Celular: menu inferior só com ícones e sem Sair", async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await entrar(page, emailEmpresa);
  const nav = page.getByRole("navigation", { name: "Menu principal" });
  await expect(nav).toBeVisible();
  await expect(nav.getByRole("link", { name: "Minha conta" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Painel" })).toBeVisible();
  await expect(nav.getByRole("button", { name: "Nova vaga" })).toBeVisible();
  await expect(nav.getByText("Sair")).toHaveCount(0);
  await page.screenshot({ path: "e2e/telas/mobile-menu.png" });
});

test("Termos atualizados bloqueiam até aceitar (tela de aceite)", async ({ page, request }) => {
  // o bloqueio em si (403 TERMOS_DESATUALIZADOS) é coberto no backend; aqui, a tela de aceite
  await entrar(page, emailEmpresa);
  const r = await request.get(`${API}/api/conta/termos`, { headers: { Authorization: "Bearer invalido" } });
  expect(r.status()).toBe(401);                       // a rota exige sessão
  await page.goto("/aceitar-termos");
  await expect(page.getByRole("heading", { name: "Termos atualizados" })).toBeVisible();
  const botao = page.getByRole("button", { name: "Aceitar e continuar" });
  await expect(botao).toBeDisabled();
  await page.getByRole("checkbox").check();
  await botao.click();
  await page.waitForURL(/\/empresa/);
});
