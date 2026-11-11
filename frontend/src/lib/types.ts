export type NivelCargo =
  | "APRENDIZ" | "ESTAGIARIO" | "TRAINEE" | "ASSISTENTE" | "ANALISTA"
  | "COORDENADOR" | "GERENTE" | "DIRETOR" | "CEO";
export type TipoContrato = "FIXO" | "TEMPORARIO" | "POR_PROJETO" | "FREELANCE";
export type ModeloTrabalho = "PRESENCIAL" | "HIBRIDO" | "REMOTO";
export type StatusVaga = "ABERTA" | "PAUSADA" | "PREENCHIDA" | "ENCERRADA";
export type StatusMatch = "SUGERIDO" | "ENVIADO" | "ENTREVISTA" | "CONTRATADO" | "RECUSADO";
export type PorteEmpresa = "MEI" | "ME" | "EPP" | "DEMAIS";
export type CategoriaTag = "DEMOGRAFIA" | "VULNERABILIDADE" | "SKILL";

export interface Empresa {
  id: string; cnpj: string; razaoSocial: string; cnaePrincipal: string; porteReceita: PorteEmpresa;
}
export interface Vaga {
  id: string; empresaId: string; nivelCargo: NivelCargo; tituloExibicao: string;
  tipoContrato: TipoContrato; modeloTrabalho: ModeloTrabalho; cepAtuacao: string; status: StatusVaga;
}
export interface Match {
  id: string; vagaId: string; tituloVaga: string; candidatoId: string; candidatoNome: string;
  ongId: string; matchScore: number; status: StatusMatch;
}
export interface AdminOng {
  id: string; cnpj: string; razaoSocial: string; cnaePrincipal: string;
  nivelCapacidade: "NIVEL_1" | "NIVEL_2" | "NIVEL_3"; cep: string; verified: boolean; createdAt: string;
}
export interface Resumo {
  empresas: number; ongs: number; ongsVerificadas: number; ongsPendentes: number;
  candidatos: number; vagasAbertas: number; matchesPorStatus: Record<StatusMatch, number>;
}
export interface Cargo { codigo: NivelCargo; rotulo: string }

export const ROTULO_CONTRATO: Record<TipoContrato, string> = {
  FIXO: "Fixo / tempo indeterminado",
  TEMPORARIO: "Temporário / tempo determinado",
  POR_PROJETO: "Por projeto / empreitada",
  FREELANCE: "Freelance / spot",
};
export const ROTULO_MODELO: Record<ModeloTrabalho, string> = {
  PRESENCIAL: "Presencial", HIBRIDO: "Híbrido", REMOTO: "Remoto",
};
export const ROTULO_STATUS: Record<StatusMatch, string> = {
  SUGERIDO: "Sugerido", ENVIADO: "Enviado", ENTREVISTA: "Entrevista", CONTRATADO: "Contratado", RECUSADO: "Recusado",
};
