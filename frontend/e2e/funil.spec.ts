import { execFileSync } from "node:child_process";
import { expect, test } from "@playwright/test";
import {
  API, DB_CONTAINER, SENHA, aguardarEmail, auth, emailsPara, entrar, ongVerificada, registrar, tokenApi,
} from "./apoio";

/**
 * Funil completo no navegador (ONG envia → empresa entrevista e contrata), avisos por e-mail sem dados pessoais,
 * relatórios de impacto, criação e busca em lote, exclusão definitiva de candidato e bloqueio por Termos.
 */
const sufixo = Date.now().toString(36);
const emailOng = `ong.f.${sufixo}@e2e.local`;
const emailEmpresa = `empresa.f.${sufixo}@e2e.local`;
const nomeCandidato = `Joana Funil ${sufixo}`;
const nomeSemContratacao = `Carlos Sem Contrato ${sufixo}`;

let ongId = "";
let empresaId = "";

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ request }) => {
  ongId = await ongVerificada(request, emailOng);
  empresaId = await registrar(request, "empresa", emailEmpresa);
  const t = await tokenApi(request, emailOng);
  for (const nome of [nomeCandidato, nomeSemContratacao]) {
    const r = await request.post(`${API}/api/ongs/${ongId}/candidatos`, {
      headers: auth(t),
      data: {
        nomeCompleto: nome, dataNascimento: "1992-03-04", cep: "13015904", modeloTrabalhoPref: "PRESENCIAL",
        escolaridade: "MEDIO", consentimentoConfirmado: true,
      },
    });
    expect(r.status(), await r.text()).toBe(201);
  }
});

test("Funil completo: ONG envia, empresa entrevista e contrata; e-mails e impacto", async ({ page, request }) => {
  const tEmp = await tokenApi(request, emailEmpresa);
  const rv = await request.post(`${API}/api/empresas/${empresaId}/vagas`, {
    headers: auth(tEmp),
    data: { nivelCargo: "ANALISTA", tipoContrato: "FIXO", modeloTrabalho: "PRESENCIAL", cepAtuacao: "13015904" },
  });
  expect(rv.status()).toBe(201);
  const vagaId = (await rv.json()).id as string;
  const rs = await request.post(`${API}/api/vagas/${vagaId}/matches/sugestoes?raioKm=15&limite=200`, { headers: auth(tEmp) });
  expect(rs.status()).toBe(201);

  // --- ONG: vê a sugestão no funil e envia à empresa
  await entrar(page, emailOng);
  await expect(page.getByText(nomeCandidato)).toBeVisible();
  const linha = page.locator("li", { hasText: nomeCandidato });
  await linha.getByRole("button", { name: "Enviar à empresa" }).click();
  await expect(page.getByText(nomeCandidato)).toHaveCount(0);            // saiu da etapa "Sugerido"
  await page.getByRole("tab", { name: "Enviado" }).click();
  await expect(page.getByText(nomeCandidato)).toBeVisible();

  // --- empresa: entrevista e contrata
  const ctx = await page.context().browser()!.newContext();
  const emp = await ctx.newPage();
  await entrar(emp, emailEmpresa);
  const card = emp.locator("article").first();
  const cand = card.locator("li", { hasText: nomeCandidato });
  await expect(cand.getByText("Enviado")).toBeVisible();
  await cand.getByRole("button", { name: "Entrevistar" }).click();
  await expect(cand.getByText("Entrevista", { exact: true })).toBeVisible();
  await cand.getByRole("button", { name: "Contratar" }).click();
  await expect(cand.getByText("Contratado")).toBeVisible();
  await expect(card.getByText("Preenchida")).toBeVisible();              // contratar fecha a vaga
  await emp.screenshot({ path: "e2e/telas/funil-contratado.png" });

  // --- avisos por e-mail: chegam, sem citar nome de candidato
  const sug = await aguardarEmail(request, emailOng, "Novas sugestões");
  const ent = await aguardarEmail(request, emailOng, "entrevista");
  const con = await aguardarEmail(request, emailOng, "contratado");
  const env = await aguardarEmail(request, emailEmpresa, "enviou um candidato");
  for (const m of [sug, ent, con, env]) expect(m.texto).not.toContain("Joana");

  // --- relatórios de impacto
  await emp.getByRole("tab", { name: "Impacto" }).click();
  await expect(emp.getByRole("heading", { name: "Impacto das suas contratações" })).toBeVisible();
  await expect(emp.locator("div", { hasText: /^1Contratações$/ }).first()).toBeVisible();
  await emp.screenshot({ path: "e2e/telas/impacto-empresa.png", fullPage: true });
  await ctx.close();

  await page.getByRole("tab", { name: "Impacto" }).click();
  await expect(page.getByRole("heading", { name: "Impacto da sua ONG" })).toBeVisible();
  await expect(page.getByText("Taxa de colocação")).toBeVisible();
  await expect(page.getByText(/Gerado em/)).toBeVisible();
  await expect(page.getByText("apenas totais")).toBeVisible();
  await page.screenshot({ path: "e2e/telas/impacto-ong.png", fullPage: true });
});

test("Exclusão definitiva de candidato sem contratação (e anonimização preservada)", async ({ page }) => {
  await entrar(page, emailOng);
  await page.getByRole("tab", { name: "Meus candidatos" }).click();
  const linha = page.locator("li", { hasText: nomeSemContratacao });
  await expect(linha).toBeVisible();
  await linha.getByRole("button", { name: "Anonimizar" }).first().click();            // abre a confirmação
  await expect(linha.getByText("Excluir definitivamente").first()).toBeVisible();
  await page.screenshot({ path: "e2e/telas/ong-exclusao.png" });
  await linha.getByRole("button", { name: "Excluir definitivamente" }).click();
  await expect(page.getByText(nomeSemContratacao)).toHaveCount(0);

  // quem foi contratado continua na lista só como anonimizável: a exclusão definitiva é recusada pela API
  const linhaContratada = page.locator("li", { hasText: nomeCandidato });
  await linhaContratada.getByRole("button", { name: "Anonimizar" }).first().click();
  await linhaContratada.getByRole("button", { name: "Excluir definitivamente" }).click();
  await expect(page.getByText(/contratação registrada/)).toBeVisible();
});

test("Vagas em lote e busca em lote distribuindo candidatos", async ({ page, request }) => {
  // candidatos extras da mesma ONG para o lote ter o que distribuir
  const t = await tokenApi(request, emailOng);
  for (let i = 1; i <= 3; i++) {
    const r = await request.post(`${API}/api/ongs/${ongId}/candidatos`, {
      headers: auth(t),
      data: {
        nomeCompleto: `Lote ${sufixo} ${i}`, dataNascimento: "1990-01-10", cep: "13015904", modeloTrabalhoPref: "PRESENCIAL",
        consentimentoConfirmado: true,
      },
    });
    expect(r.status()).toBe(201);
  }
  await entrar(page, emailEmpresa);
  await page.getByRole("button", { name: /Nova vaga/ }).first().click();
  const dlg = page.getByRole("dialog", { name: "Nova vaga" });
  await dlg.getByLabel("CEP de atuação").fill("13015904");
  await dlg.getByLabel("Quantidade de vagas").fill("3");
  await expect(dlg.getByRole("button", { name: "Publicar 3 vagas" })).toBeVisible();
  await dlg.getByRole("button", { name: "Publicar 3 vagas" }).click();
  await expect(dlg).toBeHidden();
  await expect(page.locator("article")).toHaveCount(4);                  // 3 novas + a preenchida do funil

  await expect(page.getByText("Busca em lote:")).toBeVisible();
  await page.getByRole("button", { name: "Buscar em lote" }).click();
  await expect(page.getByText(/sugestão\(ões\) em 3 vaga\(s\)/)).toBeVisible();
  await page.screenshot({ path: "e2e/telas/empresa-lote.png", fullPage: true });

  // cada candidato aparece em uma única vaga do lote
  const nomes = await page.locator("article li p.truncate").allTextContents();
  const meus = nomes.filter((n) => n.includes(`Lote ${sufixo}`));
  expect(new Set(meus).size).toBe(meus.length);
});

test("Termos desatualizados bloqueiam a API e a tela de aceite libera", async ({ page, request }) => {
  let dockerOk = true;
  try { execFileSync("docker", ["inspect", DB_CONTAINER], { stdio: "ignore" }); } catch { dockerOk = false; }
  test.skip(!dockerOk, `contêiner do banco "${DB_CONTAINER}" não encontrado (defina E2E_DB_CONTAINER)`);

  const email = `empresa.t.${sufixo}@e2e.local`;
  await registrar(request, "empresa", email);
  // a "nova versão" dos Termos: este usuário aceitou uma versão antiga
  execFileSync("docker", ["exec", DB_CONTAINER, "psql", "-U", "jaguata", "-d", "jaguata", "-c",
    `update usuarios set termos_versao = '2020-01' where email = '${email}'`], { stdio: "ignore" });

  await page.goto("/login");
  await page.getByLabel("E-mail").fill(email);
  await page.getByLabel("Senha").fill(SENHA);
  await page.getByRole("button", { name: "Entrar" }).click();
  // o painel tenta chamar a API, recebe 403 TERMOS_DESATUALIZADOS e o frontend leva à tela de aceite
  await page.waitForURL(/\/aceitar-termos/);
  await expect(page.getByRole("heading", { name: "Termos atualizados" })).toBeVisible();
  const botao = page.getByRole("button", { name: "Aceitar e continuar" });
  await expect(botao).toBeDisabled();
  await page.screenshot({ path: "e2e/telas/aceitar-termos.png" });
  await page.getByRole("checkbox").check();
  await botao.click();
  await page.waitForURL(/\/empresa/);
  await expect(page.getByText("Publique a sua primeira vaga")).toBeVisible();   // a API voltou a responder
  // e o aviso de segurança ficou registrado (sem consultar o banco: a página de Conta mostra o aceite da versão vigente)
  await page.goto("/conta");
  await expect(page.getByText(/versão 2026-11/)).toBeVisible();
  expect((await emailsPara(request, email)).length).toBeGreaterThan(0);
});
