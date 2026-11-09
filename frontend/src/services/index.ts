import { config } from "./config";
import type { Services } from "./contracts";
import { ApiError } from "./errors";
import { mockServices } from "./mock";

/**
 * Ponto único de seleção dos adapters.
 *
 * - `VITE_USE_MOCKS` ≠ "false" (padrão): adapters de DEMONSTRAÇÃO (dados em memória).
 * - `VITE_USE_MOCKS=false`: deve usar adapters HTTP — que AINDA NÃO EXISTEM porque
 *   o backend ainda não foi definido. Ao implementá-los (ver docs/API_CONTRACT.md),
 *   substitua `notImplemented` abaixo.
 */
function notImplemented(): never {
  throw new ApiError(
    "NOT_IMPLEMENTED",
    "Adapter HTTP ainda não implementado: o backend não está disponível. Use VITE_USE_MOCKS=true.",
    501,
  );
}

const httpServices = new Proxy({} as Services, { get: () => new Proxy({}, { get: () => notImplemented }) });

export const services: Services = config.useMocks ? mockServices : httpServices;
export const isDemoMode = config.useMocks;
export type { Services } from "./contracts";
export { ApiError, errorMessage } from "./errors";
