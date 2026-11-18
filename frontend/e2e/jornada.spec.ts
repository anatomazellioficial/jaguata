import { expect, test } from "@playwright/test";

/**
 * Jornada completa no navegador: ONG verificada cadastra e edita candidato e escolhe áreas de atuação; empresa
 * cria vaga com requisitos, busca com filtros, vê o ranking e "Por quê?", edita e exclui a vaga; conta com
 * avisos por e-mail e "Sair"; menu inferior no celular.
 */
import { ADMIN_EMAIL, ADMIN_SENHA, API, entrar, registrar, tokenApi, auth } from "./apoio";

const sufixo = Date.now().toString(36);
const emailOng = `ong.${sufixo}@e2e.local`;
const emailEmpresa = `empresa.${sufixo}@e2e.local`;
const nomeCandidato = `Maria E2E ${sufixo}`;

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  const ongId = await registrar(request, "ong", emailOng);
  await registrar(request, "empresa", emailEmpresa);
  const adm = await tokenApi(request, ADMIN_EMAIL, ADMIN_SENHA);
  const v = await request.post(`${API}/api/ongs/${ongId}/verificar`, { headers: auth(adm) });
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
  await expect(dlg.getByRole("button", { name: "Remover excel" })).toBeVisible();
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
  // preferências por tipo e resumo diário (aparecem com os avisos ligados)
  const resumo = page.getByRole("switch", { name: "Resumo diário" });
  await expect(resumo).not.toBeChecked();
  await resumo.click();
  await expect(resumo).toBeChecked();
  await page.reload();
  await expect(page.getByRole("switch", { name: "Resumo diário" })).toBeChecked();   // gravado no servidor
  await expect(page.getByRole("switch", { name: "Andamento do funil" })).toBeChecked();
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
