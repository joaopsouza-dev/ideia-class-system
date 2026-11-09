/**
 * "Banco" em memória usado SOMENTE pelos adapters de demonstração.
 * Reinicia a cada recarga da página. Não é persistência real.
 */
import {
  buildSeedLogs,
  seedConteudos,
  seedDisciplinas,
  seedTurmas,
  seedTurmaUsuarios,
  seedUsuarios,
} from "@/mocks/seed";
import type { ConteudoRegistro, LogAcesso, Turma, TurmaUsuario, Usuario } from "@/types";

export const db = {
  usuarios: structuredClone(seedUsuarios) as Usuario[],
  disciplinas: structuredClone(seedDisciplinas),
  turmas: structuredClone(seedTurmas) as Turma[],
  turmaUsuarios: structuredClone(seedTurmaUsuarios) as TurmaUsuario[],
  conteudos: structuredClone(seedConteudos) as ConteudoRegistro[],
  logs: buildSeedLogs() as LogAcesso[],
  /** blob URLs dos arquivos enviados nesta sessão do navegador (id do conteúdo -> url) */
  uploads: new Map<string, string>(),
  nextUserId: 100,
  nextLogId: 1000,
};

export const delay = (ms = 350) => new Promise<void>((resolve) => setTimeout(resolve, ms));
