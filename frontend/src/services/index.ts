import { config } from "./config";
import type { Services } from "./contracts";
import { mockServices } from "./mock";
import {
  httpAuthService,
  httpAcademicService,
  httpMaterialService,
  httpContentAccessService,
  httpAccessLogService,
} from "./http";

export const services: Services = {
  // AuthService real integrado via HTTP ao backend Spring Boot
  auth: config.useMockAuth ? mockServices.auth : httpAuthService,
  // AcademicService real: turmas e disciplinas do banco via backend
  academic: httpAcademicService,
  // MaterialService real: conteúdos da biblioteca integrados via backend Spring Boot
  materials: httpMaterialService,
  // Visualização protegida real: URL temporária assinada emitida pelo backend (GET /conteudos/{id}/acesso)
  content: httpContentAccessService,
  // Auditoria real: lê log_acesso e registra tentativas no backend (sem dados simulados)
  accessLog: httpAccessLogService,
};

export const isDemoMode = config.useMocks || config.useMockAuth;
export const isMockAuth = config.useMockAuth;
export const isMockData = config.useMocks;
export type { Services } from "./contracts";
export { ApiError, errorMessage } from "./errors";
export { httpClient, HttpClient, API_ENDPOINTS } from "./api";
export * from "./api/types";
export { httpAuthService, httpMaterialService } from "./http";
