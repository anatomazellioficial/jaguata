import { describe, expect, it } from "vitest";
import { EXEMPLO_CANDIDATO, EXEMPLO_VAGA, PASSOS, estadoInicial, pontuar, sugerir, type Cand, type VagaDemo } from "./dados";

const vaga: VagaDemo = { id: "v1", ...EXEMPLO_VAGA };
const cand = (c: Partial<Cand> = {}): Cand => ({ id: "x", ...EXEMPLO_CANDIDATO, ...c });

describe("protótipo: motor de pontuação", () => {
  it("os pesos somam 100 e a nota é a soma dos critérios", () => {
    const r = pontuar(cand(), vaga);
    expect(r.motivos.reduce((a, m) => a + m.maximo, 0)).toBe(100);
    expect(r.score).toBe(r.motivos.reduce((a, m) => a + m.pontos, 0));
  });

  it("vaga afirmativa só dá os 10 pontos a quem é de um grupo priorizado; vaga comum dá a todos", () => {
    const prio = (c: Cand, v: VagaDemo) => pontuar(c, v).motivos.find((m) => m.criterio === "Vaga afirmativa")!.pontos;
    expect(prio(cand({ grupos: ["Pessoas negras"] }), vaga)).toBe(10);
    expect(prio(cand({ grupos: [] }), vaga)).toBe(0);
    expect(prio(cand({ grupos: [] }), { ...vaga, afirmativa: false })).toBe(10);
  });

  it("sem ONG verificada não há sugestões; verificada, quem está além de 20 km fica de fora", () => {
    const e = estadoInicial();
    expect(sugerir(e, vaga)).toEqual([]);
    const s = sugerir({ ...e, ongVerificada: true }, vaga);
    expect(s.length).toBe(e.candidatos.filter((c) => c.km <= 20).length);
    expect(s.map((m) => m.posicao)).toEqual(s.map((_, i) => i + 1));
    expect(s.every((m, i) => i === 0 || s[i - 1].score >= m.score)).toBe(true);
  });

  it("o roteiro tem 8 passos e nenhum começa concluído", () => {
    expect(PASSOS).toHaveLength(8);
    expect(PASSOS.some((p) => p.feito(estadoInicial()))).toBe(false);
  });
});
