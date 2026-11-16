import { defineConfig } from "@playwright/test";

/**
 * Teste ponta a ponta contra a stack real (docker compose) com as APIs públicas simuladas.
 * Veja o README ("Testes de frontend") para subir tudo; variáveis: E2E_BASE, E2E_API, E2E_MAILPIT,
 * ADMIN_EMAIL e ADMIN_PASSWORD.
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: /.*\.spec\.ts/,
  timeout: 120_000,
  expect: { timeout: 10_000 },
  workers: 1,
  reporter: [["list"]],
  outputDir: "./e2e/resultados",
  use: {
    baseURL: process.env.E2E_BASE ?? "http://localhost:3010",
    headless: true,
    screenshot: "only-on-failure",
    trace: "off",
  },
});
