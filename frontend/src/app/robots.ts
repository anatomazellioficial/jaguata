import type { MetadataRoute } from "next";
import { AREAS_PRIVADAS, siteUrl } from "@/lib/site";

// lê SITE_URL em tempo de execução (não na compilação)
export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: "*", allow: "/", disallow: ["/api/", ...AREAS_PRIVADAS.map((a) => `${a}/`), ...AREAS_PRIVADAS] }],
    sitemap: `${siteUrl()}/sitemap.xml`,
    host: siteUrl(),
  };
}
