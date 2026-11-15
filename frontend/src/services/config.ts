/**
 * Configuração de integração. Valores vêm de variáveis de ambiente do Vite (.env).
 * Ver .env.example.
 */
const env =
  typeof import.meta !== "undefined" && import.meta.env
    ? import.meta.env
    : ((typeof process !== "undefined" ? process.env : {}) as unknown as Record<string, string>);

export const config = {
  /** URL base da API do backend */
  apiBaseUrl: (env.VITE_API_BASE_URL as string | undefined) || "http://localhost:8081",
  /** true para forçar o uso de autenticação simulada (mock). Por padrão é falso (autenticação real). */
  useMockAuth: (env.VITE_USE_MOCK_AUTH as string | undefined) === "true",
  /** true enquanto o backend não tiver todos os módulos implementados */
  useMocks: (env.VITE_USE_MOCKS as string | undefined) !== "false",
  /**
   * Limites de upload validados no navegador (conveniência de UX).
   * VALORES PROVISÓRIOS — o limite real deve ser definido e imposto pelo backend/storage.
   */
  maxPdfMb: Number(env.VITE_MAX_PDF_MB ?? 50),
  maxVideoMb: Number(env.VITE_MAX_VIDEO_MB ?? 500),
  pageSize: 8,
} as const;
