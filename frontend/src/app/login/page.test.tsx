import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const push = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

import LoginPage from "./page";

function resposta(status: number, corpo: unknown) {
  return Promise.resolve(new Response(JSON.stringify(corpo), { status, headers: { "content-type": "application/json" } }));
}

describe("login com segundo fator", () => {
  beforeEach(() => push.mockClear());
  afterEach(() => vi.restoreAllMocks());

  async function preencher() {
    await userEvent.type(screen.getByLabelText("E-mail"), "admin@jaguata.local");
    await userEvent.type(screen.getByLabelText("Senha"), "senha-longa-123");
  }

  it("pede o código quando o servidor responde TOTP_REQUERIDO e envia junto no segundo envio", async () => {
    const chamadas: string[] = [];
    vi.stubGlobal("fetch", vi.fn((_url: string, init: RequestInit) => {
      chamadas.push(String(init.body));
      return chamadas.length === 1
        ? resposta(401, { codigo: "TOTP_REQUERIDO", detail: "Informe o código." })
        : resposta(200, { papel: "ADMIN" });
    }));

    render(<LoginPage />);
    await preencher();
    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    const campo = await screen.findByLabelText(/Código do app autenticador/);
    expect(JSON.parse(chamadas[0])).not.toHaveProperty("codigoTotp");

    await userEvent.type(campo, "12a3456");   // letras são descartadas; só 6 dígitos
    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/admin"));
    expect(JSON.parse(chamadas[1])).toMatchObject({ email: "admin@jaguata.local", codigoTotp: "123456" });
  });

  it("mostra o erro de credenciais sem pedir código", async () => {
    vi.stubGlobal("fetch", vi.fn(() => resposta(401, { detail: "E-mail ou senha incorretos." })));
    render(<LoginPage />);
    await preencher();
    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));
    expect(await screen.findByText("E-mail ou senha incorretos.")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Código do app autenticador/)).not.toBeInTheDocument();
  });
});
