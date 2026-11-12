/**
 * Mapeamento dos endpoints reais implementados no backend Ideia Space (/api/v1/*).
 * Apenas endpoints comprovadamente existentes nos controllers do Spring Boot.
 */

export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: "/api/v1/auth/login",
    PROVISION: "/api/v1/auth/provision",
  },
  DISCIPLINAS: {
    BASE: "/api/v1/disciplinas",
    BY_ID: (id: number | string) => `/api/v1/disciplinas/${id}`,
  },
  TURMAS: {
    BASE: "/api/v1/turmas",
    BY_ID: (id: number | string) => `/api/v1/turmas/${id}`,
    PARTICIPANTES: (turmaId: number | string) => `/api/v1/turmas/${turmaId}/participantes`,
    REMOVER_PARTICIPANTE: (turmaId: number | string, vinculoId: number | string) =>
      `/api/v1/turmas/${turmaId}/participantes/${vinculoId}`,
  },
  CONTEUDOS: {
    BASE: "/api/v1/conteudos",
    BY_ID: (id: number | string) => `/api/v1/conteudos/${id}`,
  },
} as const;
