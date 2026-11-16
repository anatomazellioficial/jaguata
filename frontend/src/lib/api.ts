"use client";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public campos?: Record<string, string>,
    /** Código estável enviado pelo backend (ex.: EMAIL_NAO_VERIFICADO). */
    public codigo?: string,
  ) {
    super(message);
  }
}

const AREAS_PRIVADAS = ["/empresa", "/ong", "/admin", "/conta"];

async function parse<T>(res: Response): Promise<T> {
  if (res.ok) {
    return (res.status === 204 || res.status === 202 ? undefined : await res.json()) as T;
  }
  const problem = await res.json().catch(() => null);
  // sessão expirada numa área logada: volta ao login (o BFF já tentou renovar sozinho)
  if (res.status === 401 && typeof window !== "undefined" && AREAS_PRIVADAS.some((a) => location.pathname.startsWith(a))) {
    location.href = `/login?next=${encodeURIComponent(location.pathname)}`;
  }
  const retry = Number(res.headers.get("Retry-After"));
  const espera = retry > 60 ? `${Math.ceil(retry / 60)} min` : retry > 0 ? `${retry} s` : "um instante";
  const msg =
    problem?.codigo === "EMAIL_NAO_VERIFICADO" ? problem.detail
    : res.status === 403 ? "Você não tem permissão para esta ação."
    : res.status === 429 ? `Muitas tentativas. Aguarde ${espera} e tente de novo.`
    : problem?.detail ?? "Algo deu errado. Tente novamente.";
  throw new ApiError(res.status, msg, problem?.campos, problem?.codigo);
}

/** Chamadas ao backend via proxy (os tokens vão em cookies httpOnly, nunca no JS). */
export async function api<T>(path: string, init?: { method?: string; body?: unknown }): Promise<T> {
  const res = await fetch(`/api/proxy/${path}`, {
    method: init?.method ?? "GET",
    headers: init?.body ? { "Content-Type": "application/json" } : undefined,
    body: init?.body ? JSON.stringify(init.body) : undefined,
  });
  return parse<T>(res);
}

/** Baixa a resposta de uma rota como arquivo (ex.: exportação de dados pessoais em JSON). */
export async function baixar(path: string, nomeArquivo: string) {
  const res = await fetch(`/api/proxy/${path}`);
  if (!res.ok) await parse(res);
  const blob = new Blob([await res.text()], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nomeArquivo;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export async function login(email: string, senha: string) {
  const res = await fetch("/api/session/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, senha }),
  });
  return parse<{ papel: "EMPRESA" | "ONG" | "ADMIN"; empresaId?: string; ongId?: string }>(res);
}

export async function logout(destino = "/") {
  await fetch("/api/session/logout", { method: "POST" });
  location.href = destino;
}

export function getSession() {
  return fetch("/api/session").then((r) =>
    parse<{ papel: "EMPRESA" | "ONG" | "ADMIN"; empresaId?: string; ongId?: string }>(r),
  );
}
