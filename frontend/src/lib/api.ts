"use client";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public campos?: Record<string, string>,
  ) {
    super(message);
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.ok) {
    return (res.status === 204 ? undefined : await res.json()) as T;
  }
  const problem = await res.json().catch(() => null);
  if (res.status === 401 && typeof window !== "undefined" && !location.pathname.startsWith("/login")) {
    location.href = `/login?next=${encodeURIComponent(location.pathname)}`;
  }
  const retry = Number(res.headers.get("Retry-After"));
  const espera = retry > 60 ? `${Math.ceil(retry / 60)} min` : retry > 0 ? `${retry} s` : "um instante";
  const msg =
    res.status === 403 ? "Você não tem permissão para esta ação."
    : res.status === 429 ? `Muitas tentativas. Aguarde ${espera} e tente de novo.`
    : problem?.detail ?? "Algo deu errado. Tente novamente.";
  throw new ApiError(res.status, msg, problem?.campos);
}

/** Chamadas ao backend via proxy (o JWT vai no cookie httpOnly, nunca no JS). */
export async function api<T>(path: string, init?: { method?: string; body?: unknown }): Promise<T> {
  const res = await fetch(`/api/proxy/${path}`, {
    method: init?.method ?? "GET",
    headers: init?.body ? { "Content-Type": "application/json" } : undefined,
    body: init?.body ? JSON.stringify(init.body) : undefined,
  });
  return parse<T>(res);
}

export async function login(email: string, senha: string) {
  const res = await fetch("/api/session/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, senha }),
  });
  return parse<{ papel: "EMPRESA" | "ONG" | "ADMIN"; empresaId?: string; ongId?: string }>(res);
}

export async function logout() {
  await fetch("/api/session/logout", { method: "POST" });
  location.href = "/";
}

export function getSession() {
  return fetch("/api/session").then((r) =>
    parse<{ papel: "EMPRESA" | "ONG" | "ADMIN"; empresaId?: string; ongId?: string }>(r),
  );
}
