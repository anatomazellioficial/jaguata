import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ChipGroup } from "./ui";

const OPCOES = [
  { codigo: "CLT", rotulo: "CLT" },
  { codigo: "PJ", rotulo: "PJ" },
  { codigo: "COOP", rotulo: "Cooperado" },
];

function Caixa({ max }: { max?: number }) {
  const [v, setV] = useState<string[]>([]);
  return (
    <>
      <ChipGroup label="Regimes" options={OPCOES} value={v} onChange={setV} max={max} />
      <output data-testid="sel">{v.join(",")}</output>
    </>
  );
}

describe("ChipGroup", () => {
  it("liga e desliga opções, indicando o estado por aria-pressed", async () => {
    render(<Caixa />);
    const clt = screen.getByRole("button", { name: "CLT" });
    expect(clt).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(clt);
    expect(clt).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByTestId("sel")).toHaveTextContent("CLT");
    await userEvent.click(clt);
    expect(screen.getByTestId("sel")).toHaveTextContent("");
  });

  it("respeita o limite máximo, mas deixa desmarcar", async () => {
    render(<Caixa max={2} />);
    await userEvent.click(screen.getByRole("button", { name: "CLT" }));
    await userEvent.click(screen.getByRole("button", { name: "PJ" }));
    expect(screen.getByRole("button", { name: "Cooperado" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "PJ" }));
    expect(screen.getByRole("button", { name: "Cooperado" })).toBeEnabled();
  });
});
