/** Endereço público do site (SITE_URL em produção; ex.: https://jaguata.org.br). Lido em tempo de execução. */
export function siteUrl(): string {
  const bruto = process.env.SITE_URL || "http://localhost:3000";
  return bruto.replace(/\/+$/, "");
}

export const SITE_NOME = "Jaguatá";
export const SITE_TITULO = "Jaguatá — talento chancelado, vaga preenchida";
export const SITE_DESCRICAO =
  "Plataforma que conecta PMEs a ONGs: a ONG chancela e envia talentos 18+ em vulnerabilidade social para as vagas da sua empresa, com match por território, perfil e setor.";

/** Páginas públicas e indexáveis (entram no sitemap). */
export const PAGINAS_PUBLICAS = ["/", "/registro", "/login", "/termos", "/privacidade"];

/** Áreas logadas e telas de fluxo: nunca devem aparecer em buscadores. */
export const AREAS_PRIVADAS = [
  "/empresa", "/ong", "/admin", "/conta", "/aceitar-termos", "/verificar-email", "/redefinir-senha", "/esqueci-senha",
];
