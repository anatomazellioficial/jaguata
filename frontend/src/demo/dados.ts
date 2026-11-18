/**
 * Protótipo de demonstração: dados 100% fictícios e motor de pontuação simplificado (mesmos critérios e pesos do
 * Score v2 real, mas calculado no navegador). Nada aqui chama o backend nem guarda dado de pessoa real.
 */
import type { ModeloTrabalho, StatusMatch } from "@/lib/types";

export type Papel = "publico" | "admin" | "ong" | "empresa";

export interface Cand {
  id: string; nome: string; idade: number; bairro: string; km: number; modelo: ModeloTrabalho;
  escolaridade: "FUND" | "MEDIO" | "TECNICO" | "SUPERIOR"; skills: string[]; regime: "CLT" | "PJ";
  /** Autodeclaração: fica só com a ONG; a empresa nunca a recebe. */
  grupos: string[];
}
export interface VagaDemo {
  id: string; titulo: string; modelo: ModeloTrabalho; cep: string; escolaridadeMin: Cand["escolaridade"] | "";
  skills: string[]; afirmativa: boolean; grupos: string[]; justificativa: string;
}
export interface Motivo { criterio: string; pontos: number; maximo: number; nota: string }
export interface MatchDemo {
  id: string; vagaId: string; candId: string; status: StatusMatch; score: number; motivos: Motivo[]; posicao: number;
}
export interface Estado {
  consultaFeita: boolean; ongVerificada: boolean;
  candidatos: Cand[]; vagas: VagaDemo[]; matches: MatchDemo[]; visitouImpacto: boolean;
}

export const ROTULO_ESC = { FUND: "Fundamental", MEDIO: "Ensino médio", TECNICO: "Técnico", SUPERIOR: "Superior" } as const;
const NIVEL = { FUND: 0, MEDIO: 1, TECNICO: 2, SUPERIOR: 3 } as const;

export const GRUPOS = ["Mulheres", "Pessoas negras", "50+", "PCD", "Egressos do sistema prisional"];

export const EMPRESA = {
  razao: "Padaria Pão Nosso LTDA", cnpj: "12.345.678/0001-95", porte: "ME", cnae: "Fabricação de produtos de panificação (1091-1/02)",
  cidade: "Campinas/SP",
};
export const ONG = {
  razao: "Instituto Girassol", cnpj: "98.765.432/0001-10", cnae: "Atividades de associações de defesa de direitos sociais (9430-8/00)",
  cidade: "Campinas/SP", areas: ["Alimentação", "Comércio"],
};

export const SKILLS_SUGERIDAS = ["Atendimento", "Caixa", "Panificação", "Excel", "Manipulação de alimentos", "CNH B", "Estoque"];

const base: Cand[] = [
  { id: "c1", nome: "Marina Souza", idade: 29, bairro: "Taquaral", km: 3, modelo: "PRESENCIAL", escolaridade: "MEDIO", skills: ["Atendimento", "Caixa"], regime: "CLT", grupos: ["Mulheres"] },
  { id: "c2", nome: "Carlos Eduardo Lima", idade: 52, bairro: "Barão Geraldo", km: 9, modelo: "PRESENCIAL", escolaridade: "TECNICO", skills: ["Panificação", "Manipulação de alimentos", "Estoque"], regime: "CLT", grupos: ["50+", "Pessoas negras"] },
  { id: "c3", nome: "Juliana Ferreira", idade: 24, bairro: "Cambuí", km: 5, modelo: "HIBRIDO", escolaridade: "SUPERIOR", skills: ["Excel", "Atendimento"], regime: "CLT", grupos: [] },
  { id: "c4", nome: "Rafael Nogueira", idade: 34, bairro: "Jardim Eulina", km: 14, modelo: "PRESENCIAL", escolaridade: "MEDIO", skills: ["Panificação", "CNH B"], regime: "PJ", grupos: ["Egressos do sistema prisional"] },
  { id: "c5", nome: "Aline Barbosa", idade: 41, bairro: "Vila Industrial", km: 22, modelo: "PRESENCIAL", escolaridade: "FUND", skills: ["Caixa", "Estoque"], regime: "CLT", grupos: ["Mulheres", "PCD"] },
];

/** Estado inicial da demonstração (ONG ainda pendente, sem vagas). */
export function estadoInicial(): Estado {
  return { consultaFeita: false, ongVerificada: false, candidatos: base.map((c) => ({ ...c })), vagas: [], matches: [], visitouImpacto: false };
}

/** Candidato que o apresentador cadastra no passo 2 (botão "Preencher exemplo"). */
export const EXEMPLO_CANDIDATO: Omit<Cand, "id"> = {
  nome: "Davi Martins", idade: 31, bairro: "Centro", km: 2, modelo: "PRESENCIAL", escolaridade: "MEDIO",
  skills: ["Panificação", "Atendimento", "Caixa"], regime: "CLT", grupos: ["Pessoas negras"],
};

/** Vaga de exemplo do passo 3. */
export const EXEMPLO_VAGA: Omit<VagaDemo, "id"> = {
  titulo: "Auxiliar de padaria", modelo: "PRESENCIAL", cep: "13015-904", escolaridadeMin: "MEDIO",
  skills: ["Panificação", "Atendimento", "Caixa"], afirmativa: true, grupos: ["Pessoas negras", "50+"],
  justificativa: "Ampliar a presença de pessoas negras e 50+ na equipe de produção.",
};

const norm = (s: string) => s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

/** Score v2 simplificado: Território 30 · Modelo 10 · Setor 15 · Formação 15 · Habilidades 15 · Regime 5 · Prioridade 10. */
export function pontuar(c: Cand, v: VagaDemo): { score: number; motivos: Motivo[] } {
  const km = c.km;
  const terr = v.modelo === "REMOTO" ? 30 : Math.max(0, Math.round(30 * (1 - km / 20)));
  const modelo = c.modelo === v.modelo ? 10 : c.modelo === "HIBRIDO" || v.modelo === "HIBRIDO" ? 5 : 0;
  const setor = 15;   // a ONG atua em Alimentação e a empresa é uma padaria
  const form = !v.escolaridadeMin ? 15 : NIVEL[c.escolaridade] >= NIVEL[v.escolaridadeMin] ? 15 : NIVEL[c.escolaridade] === NIVEL[v.escolaridadeMin] - 1 ? 5 : 0;
  const tem = v.skills.filter((s) => c.skills.some((x) => norm(x) === norm(s))).length;
  const skills = v.skills.length ? Math.round((15 * tem) / v.skills.length) : 15;
  const regime = c.regime === "CLT" ? 5 : 2;
  const prio = !v.afirmativa ? 10 : c.grupos.some((g) => v.grupos.includes(g)) ? 10 : 0;
  const motivos: Motivo[] = [
    { criterio: "Proximidade", pontos: terr, maximo: 30, nota: v.modelo === "REMOTO" ? "Vaga remota" : `A cerca de ${km} km` },
    { criterio: "Modelo de trabalho", pontos: modelo, maximo: 10, nota: modelo === 10 ? "Preferência coincide" : modelo === 5 ? "Híbrido de um dos lados" : "Preferências diferentes" },
    { criterio: "Setor x áreas da ONG", pontos: setor, maximo: 15, nota: "Setor dentro das áreas da ONG" },
    { criterio: "Formação", pontos: form, maximo: 15, nota: v.escolaridadeMin ? (form === 15 ? "Atende ao mínimo" : form === 5 ? "Um nível abaixo" : "Abaixo do mínimo") : "Sem exigência" },
    { criterio: "Habilidades", pontos: skills, maximo: 15, nota: v.skills.length ? `${tem} de ${v.skills.length} habilidades` : "Sem exigência" },
    { criterio: "Regime", pontos: regime, maximo: 5, nota: c.regime === "CLT" ? "Prefere CLT" : "Prefere PJ" },
    { criterio: "Vaga afirmativa", pontos: prio, maximo: 10, nota: v.afirmativa ? (prio === 10 ? "Pertence a um grupo priorizado" : "Fora dos grupos priorizados") : "Vaga comum" },
  ];
  return { score: motivos.reduce((a, m) => a + m.pontos, 0), motivos };
}

/** Candidatos elegíveis (dentro de 20 km) ordenados por score; ONG precisa estar verificada. */
export function sugerir(e: Estado, vaga: VagaDemo): MatchDemo[] {
  if (!e.ongVerificada) return [];
  const ja = new Set(e.matches.filter((m) => m.vagaId === vaga.id).map((m) => m.candId));
  const novos = e.candidatos
    .filter((c) => !ja.has(c.id) && (vaga.modelo === "REMOTO" || c.km <= 20))
    .map((c) => ({ c, ...pontuar(c, vaga) }))
    .sort((a, b) => b.score - a.score || a.c.nome.localeCompare(b.c.nome));
  return novos.map((x, i) => ({
    id: `m-${vaga.id}-${x.c.id}`, vagaId: vaga.id, candId: x.c.id, status: "SUGERIDO" as StatusMatch,
    score: x.score, motivos: x.motivos, posicao: ja.size + i + 1,
  }));
}

export interface Passo { n: number; papel: Papel; aba: string; titulo: string; texto: string; feito: (e: Estado) => boolean }

export const PASSOS: Passo[] = [
  { n: 1, papel: "publico", aba: "cadastro", titulo: "Cadastro só com o CNPJ",
    texto: "A ONG (ou empresa) informa o CNPJ e a plataforma busca os dados na Receita. Se estiver inativo, não cadastra.",
    feito: (e) => e.consultaFeita },
  { n: 2, papel: "admin", aba: "ongs", titulo: "Administração verifica a ONG",
    texto: "Toda ONG nasce não verificada e só entra nos matches depois de aprovada por uma pessoa da administração.",
    feito: (e) => e.ongVerificada },
  { n: 3, papel: "ong", aba: "candidatos", titulo: "ONG cadastra o assistido",
    texto: "A ONG conhece as pessoas e cadastra perfil, habilidades e, se a pessoa quiser, autodeclarações (que a empresa nunca vê).",
    feito: (e) => e.candidatos.length > base.length },
  { n: 4, papel: "empresa", aba: "vagas", titulo: "Empresa publica a vaga",
    texto: "Cargo, modelo, CEP, requisitos e, se quiser, vaga afirmativa com justificativa (que fica na auditoria).",
    feito: (e) => e.vagas.length > 0 },
  { n: 5, papel: "empresa", aba: "vagas", titulo: "Match explicável",
    texto: "O motor ranqueia por proximidade, formação, habilidades etc. A empresa vê a posição e o “Por quê?”, nunca a nota.",
    feito: (e) => e.matches.length > 0 },
  { n: 6, papel: "ong", aba: "funil", titulo: "ONG envia o candidato",
    texto: "A ONG revisa as sugestões (com a nota completa) e decide quem enviar à empresa.",
    feito: (e) => e.matches.some((m) => m.status !== "SUGERIDO") },
  { n: 7, papel: "empresa", aba: "vagas", titulo: "Entrevista e contratação",
    texto: "A empresa chama para entrevista e registra a contratação; a ONG é avisada por e-mail, sem nome de candidato.",
    feito: (e) => e.matches.some((m) => m.status === "CONTRATADO") },
  { n: 8, papel: "ong", aba: "impacto", titulo: "Relatório de impacto",
    texto: "Só totais (colocação, tempo até contratar, parceiros), prontos para imprimir ou salvar em PDF. Sem recortes por grupo.",
    feito: (e) => e.visitouImpacto },
];
