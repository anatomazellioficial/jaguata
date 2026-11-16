import { afterEach, describe, expect, it, vi } from "vitest";
import manifest from "./manifest";
import robots from "./robots";
import sitemap from "./sitemap";
import { AREAS_PRIVADAS, PAGINAS_PUBLICAS, siteUrl } from "@/lib/site";

afterEach(() => vi.unstubAllEnvs());

describe("SEO", () => {
  it("siteUrl usa SITE_URL sem barra final e tem padrão local", () => {
    vi.stubEnv("SITE_URL", "https://jaguata.org.br///");
    expect(siteUrl()).toBe("https://jaguata.org.br");
    vi.stubEnv("SITE_URL", "");
    expect(siteUrl()).toBe("http://localhost:3000");
  });

  it("robots.txt bloqueia API e áreas privadas e aponta o sitemap", () => {
    vi.stubEnv("SITE_URL", "https://jaguata.org.br");
    const r = robots();
    const regra = Array.isArray(r.rules) ? r.rules[0] : r.rules;
    const bloqueados = ([] as string[]).concat(regra.disallow ?? []);
    expect(bloqueados).toContain("/api/");
    for (const a of AREAS_PRIVADAS) expect(bloqueados).toContain(a);
    expect(r.sitemap).toBe("https://jaguata.org.br/sitemap.xml");
  });

  it("sitemap lista só páginas públicas, nunca áreas privadas", () => {
    vi.stubEnv("SITE_URL", "https://jaguata.org.br");
    const urls = sitemap().map((s) => s.url);
    expect(urls).toHaveLength(PAGINAS_PUBLICAS.length);
    expect(urls).toContain("https://jaguata.org.br");
    expect(urls).toContain("https://jaguata.org.br/registro");
    for (const a of AREAS_PRIVADAS) expect(urls.some((u) => u.endsWith(a))).toBe(false);
  });

  it("manifest traz o ícone do J (comum e maskable) e as cores da marca", () => {
    const m = manifest();
    expect(m.theme_color).toBe("#102e50");
    expect(m.icons?.some((i) => i.purpose === "maskable")).toBe(true);
    expect(m.icons?.map((i) => i.src)).toContain("/icon-512.png");
  });
});
