// Auditoria Lighthouse das páginas públicas (celular, o caso mais exigente) com limites que falham o CI.
// Uso: npm run lighthouse            (LH_BASE=http://localhost:3010 por padrão; CHROME_PATH se o Chrome não for achado)
//
// Limites: acessibilidade, boas práticas e SEO = 100 (são determinísticos). Desempenho varia com a máquina:
// abaixo de PISO_DESEMPENHO falha; abaixo de META_DESEMPENHO só avisa.
import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";

const BASE = (process.env.LH_BASE ?? "http://localhost:3010").replace(/\/+$/, "");
const PAGINAS = ["/", "/login", "/registro", "/termos"];
const META_DESEMPENHO = 85;
const PISO_DESEMPENHO = 60;
const saida = "e2e/resultados/lh";
mkdirSync(saida, { recursive: true });

// chama o Node direto no executável do Lighthouse (sem shell): os argumentos com espaço chegam intactos no Windows
const cli = createRequire(import.meta.url).resolve("lighthouse/cli/index.js");

let falhou = false;
console.log("página".padEnd(14), "perf  a11y  bp   seo   FCP     LCP     TBT      CLS");
for (const rota of PAGINAS) {
  const arquivo = `${saida}/ci-${rota === "/" ? "home" : rota.slice(1)}.json`;
  const r = spawnSync(process.execPath, [
    cli, BASE + rota, "--only-categories=performance,accessibility,best-practices,seo", "--output=json",
    `--output-path=${arquivo}`, "--chrome-flags=--headless=new --no-sandbox", "--quiet",
  ], { stdio: "ignore", timeout: 240_000 });
  if (r.status !== 0) { console.error(`  ✗ ${rota}: o Lighthouse falhou (status ${r.status})`); falhou = true; continue; }

  const lhr = JSON.parse(readFileSync(arquivo, "utf8"));
  const nota = (c) => Math.round(lhr.categories[c].score * 100);
  const a = lhr.audits;
  const [perf, a11y, bp, seo] = ["performance", "accessibility", "best-practices", "seo"].map(nota);
  console.log(
    rota.padEnd(14), String(perf).padEnd(5), String(a11y).padEnd(5), String(bp).padEnd(4), String(seo).padEnd(5),
    a["first-contentful-paint"].displayValue.padEnd(7), a["largest-contentful-paint"].displayValue.padEnd(7),
    a["total-blocking-time"].displayValue.padEnd(8), a["cumulative-layout-shift"].displayValue,
  );
  for (const [nome, v] of [["acessibilidade", a11y], ["boas práticas", bp], ["SEO", seo]]) {
    if (v < 100) { console.error(`  ✗ ${rota}: ${nome} = ${v} (exigido: 100)`); falhou = true; }
  }
  if (perf < PISO_DESEMPENHO) { console.error(`  ✗ ${rota}: desempenho = ${perf} (piso: ${PISO_DESEMPENHO})`); falhou = true; }
  else if (perf < META_DESEMPENHO) console.warn(`  ! ${rota}: desempenho = ${perf} (meta: ${META_DESEMPENHO}; varia com a máquina)`);
}
process.exit(falhou ? 1 : 0);
