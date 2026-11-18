import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { writeFileSync } from "node:fs";
import { API, ADMIN_EMAIL, ADMIN_SENHA, SENHA, auth, entrar, ongVerificada, registrar, tokenApi } from "./apoio";

/**
 * Varredura de acessibilidade (axe-core, WCAG 2.0/2.1/2.2 nível AA) das páginas públicas e das áreas logadas, em
 * desktop e celular, incluindo estados interativos (diálogos abertos, abas, painéis expansíveis).
 * Falha se houver violação. Com A11Y_RELATORIO=1 só imprime tudo e grava e2e/resultados/a11y.json (modo medição).
 */
const MODO_MEDICAO = process.env.A11Y_RELATORIO === "1";
const TAGS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa", "best-practice"];

interface Achado { pagina: string; viewport: string; regra: string; impacto: string; nos: number; exemplo: string; ajuda: string }
const achados: Achado[] = [];

async function varrer(page: Page, rotulo: string, viewport: string, esperaMs = 1800) {
  // espera as animações de entrada assentarem: o contraste só vale no estado final (opacidade 1),
  // não no meio de um fade (a página inicial tem entradas escalonadas de ~2,5 s)
  await page.waitForTimeout(esperaMs);
  const r = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  for (const v of r.violations) {
    achados.push({
      pagina: rotulo, viewport, regra: v.id, impacto: v.impact ?? "?", nos: v.nodes.length,
      exemplo: v.nodes[0]?.html.slice(0, 160) ?? "", ajuda: v.help,
    });
  }
}

const emailOng = `ong.a.${Date.now().toString(36)}@e2e.local`;
const emailEmpresa = `empresa.a.${Date.now().toString(36)}@e2e.local`;
let ongId = "";
let empresaId = "";

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  ongId = await ongVerificada(request, emailOng);
  empresaId = await registrar(request, "empresa", emailEmpresa);
  const t = await tokenApi(request, emailOng);
  const r = await request.post(`${API}/api/ongs/${ongId}/candidatos`, {
    headers: auth(t),
    data: { nomeCompleto: "Ana Acessível", dataNascimento: "1991-02-03", cep: "13015904", modeloTrabalhoPref: "PRESENCIAL",
      escolaridade: "MEDIO", consentimentoConfirmado: true },
  });
  expect(r.status()).toBe(201);
  const te = await tokenApi(request, emailEmpresa);
  const v = await request.post(`${API}/api/empresas/${empresaId}/vagas`, {
    headers: auth(te),
    data: { nivelCargo: "ANALISTA", tipoContrato: "FIXO", modeloTrabalho: "PRESENCIAL", cepAtuacao: "13015904" },
  });
  const vagaId = (await v.json()).id as string;
  await request.post(`${API}/api/vagas/${vagaId}/matches/sugestoes?raioKm=15&limite=50`, { headers: auth(te) });
});

test.afterAll(() => {
  writeFileSync("e2e/resultados/a11y.json", JSON.stringify(achados, null, 2));
  const por = new Map<string, Achado[]>();
  for (const a of achados) por.set(a.regra, [...(por.get(a.regra) ?? []), a]);
  // eslint-disable-next-line no-console
  console.log("\n=== RESUMO A11Y ===");
  for (const [regra, lista] of por) {
    console.log(`${lista[0].impacto.padEnd(9)} ${regra}  (${lista.length} página(s)) — ${lista[0].ajuda}`);
    console.log(`           ex.: ${lista[0].pagina} [${lista[0].viewport}] ${lista[0].exemplo}`);
  }
});

for (const vp of [{ nome: "desktop", w: 1280, h: 900 }, { nome: "celular", w: 375, h: 812 }]) {
  test.describe(`axe — ${vp.nome}`, () => {
    test.use({ viewport: { width: vp.w, height: vp.h } });

    test("páginas públicas", async ({ page }) => {
      for (const rota of ["/", "/login", "/registro", "/esqueci-senha", "/termos", "/privacidade",
        "/redefinir-senha?token=x", "/verificar-email?token=x"]) {
        await page.goto(rota);
        await varrer(page, rota, vp.nome, rota === "/" ? 3800 : 1800);
      }
      await page.goto("/registro");
      await page.getByRole("tab", { name: "Sou ONG" }).click();
      await varrer(page, "/registro (ONG)", vp.nome);
    });

    test("ONG logada", async ({ page }) => {
      await entrar(page, emailOng);
      await varrer(page, "/ong (Funil)", vp.nome);
      for (const aba of ["Meus candidatos", "Áreas de atuação", "Impacto"]) {
        await page.getByRole("tab", { name: aba }).click();
        await varrer(page, `/ong (${aba})`, vp.nome);
      }
      await page.getByRole("tab", { name: "Meus candidatos" }).click();
      await page.getByRole("button", { name: "Editar" }).first().click();
      await page.getByText("Campinas/SP").waitFor();      // resultado da consulta de CEP
      await varrer(page, "/ong (editar candidato)", vp.nome);
      await page.keyboard.press("Escape");
      await page.goto("/conta");
      await varrer(page, "/conta (ONG)", vp.nome);
    });

    test("empresa logada", async ({ page }) => {
      await entrar(page, emailEmpresa);
      await expect(page.locator("article").first()).toBeVisible();
      await varrer(page, "/empresa (vagas)", vp.nome);
      const card = page.locator("article").first();
      await card.getByRole("button", { name: "Filtros da busca" }).click();
      await card.getByRole("button", { name: "Por quê?" }).first().click().catch(() => undefined);
      await varrer(page, "/empresa (filtros e Por quê?)", vp.nome);
      await page.getByRole("button", { name: /Nova vaga/ }).first().click();
      const dlg = page.getByRole("dialog", { name: "Nova vaga" });
      await dlg.getByText("Vaga afirmativa").click();
      await varrer(page, "/empresa (nova vaga, afirmativa)", vp.nome);
      await page.keyboard.press("Escape");
      await page.getByRole("tab", { name: "Impacto" }).click();
      await varrer(page, "/empresa (Impacto)", vp.nome);
      await page.goto("/conta");
      await varrer(page, "/conta (empresa)", vp.nome);
    });

    test("administrador", async ({ page }) => {
      await page.goto("/login");
      await page.getByLabel("E-mail").fill(ADMIN_EMAIL);
      await page.getByLabel("Senha").fill(ADMIN_SENHA);
      await page.getByRole("button", { name: "Entrar" }).click();
      await page.waitForURL(/\/admin/);
      await varrer(page, "/admin", vp.nome);
      await page.goto("/conta");
      await expect(page.getByText("Verificação em duas etapas")).toBeVisible();
      await varrer(page, "/conta (admin, 2FA)", vp.nome);
    });
  });
}

test("sem violações", async () => {
  test.skip(MODO_MEDICAO, "modo medição: veja o resumo impresso");
  const graves = achados.map((a) => `${a.impacto} ${a.regra} @ ${a.pagina} [${a.viewport}] — ${a.ajuda}`);
  expect(graves, "violações de acessibilidade (axe, WCAG 2.2 AA)").toEqual([]);
});

// SENHA é usada só para evitar aviso de import não usado em alguns ambientes de lint
void SENHA;
