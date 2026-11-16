import type { MetadataRoute } from "next";
import { PAGINAS_PUBLICAS, siteUrl } from "@/lib/site";

export const dynamic = "force-dynamic";

const PRIORIDADE: Record<string, number> = { "/": 1, "/registro": 0.8, "/login": 0.5, "/termos": 0.3, "/privacidade": 0.3 };

export default function sitemap(): MetadataRoute.Sitemap {
  const base = siteUrl();
  return PAGINAS_PUBLICAS.map((p) => ({
    url: p === "/" ? base : `${base}${p}`,
    changeFrequency: p === "/" ? "weekly" : "monthly",
    priority: PRIORIDADE[p] ?? 0.3,
  }));
}
