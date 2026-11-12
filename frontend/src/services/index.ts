import { config } from "./config";
import type { Services } from "./contracts";
import { mockServices } from "./mock";
import { httpAuthService, httpAcademicService } from "./http";

export const services: Services = {
  // AuthService real integrado via HTTP ao backend Spring Boot
  auth: config.useMockAuth ? mockServices.auth : httpAuthService,
  // AcademicService real: turmas e disciplinas do banco via backend
  academic: httpAcademicService,
  // Demais serviços mantidos temporariamente em mock até suas respectivas etapas de integração
  materials: mockServices.materials,
  content: mockServices.content,
  accessLog: mockServices.accessLog,
};

export const isDemoMode = config.useMocks || config.useMockAuth;
export const isMockAuth = config.useMockAuth;
export const isMockData = config.useMocks;
export type { Services } from "./contracts";
export { ApiError, errorMessage } from "./errors";
export { httpClient, HttpClient, API_ENDPOINTS } from "./api";
export * from "./api/types";
export { httpAuthService } from "./http";
