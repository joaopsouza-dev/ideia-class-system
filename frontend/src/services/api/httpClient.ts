import { config } from "../config";
import { ApiError, type ApiErrorCode } from "../errors";
import type {
  BackendErrorResponse,
  RequestOptions,
  UploadOptions,
  UploadProgress,
} from "./types";

export const TOKEN_STORAGE_KEY = "ideiaspace:auth_token";

export type AuthFailureCallback = (status: 401 | 403, error: ApiError) => void;

/**
 * Cliente HTTP centralizado para comunicação com a API backend do Ideia Space.
 *
 * Suporta:
 * - URL base configurável por variável de ambiente (VITE_API_BASE_URL)
 * - Injeção automática de JWT no header Authorization
 * - Mapeamento e normalização centralizada de erros da API
 * - Manipulação automática de JSON e multipart/form-data
 * - Notificações de 401 (desautenticação/expiração) e 403 (acesso negado)
 * - Cancelamento via AbortSignal e progresso de upload
 */
export class HttpClient {
  private baseUrl: string;
  private token: string | null = null;
  private unauthorizedListeners = new Set<AuthFailureCallback>();
  private forbiddenListeners = new Set<AuthFailureCallback>();

  constructor(baseUrl?: string) {
    this.baseUrl = (baseUrl ?? config.apiBaseUrl).replace(/\/+$/, "");
    this.initTokenFromStorage();
  }

  /**
   * Inicializa o token do armazenamento de sessão do navegador.
   */
  private initTokenFromStorage(): void {
    if (typeof window === "undefined" || !window.sessionStorage) return;
    try {
      const stored = window.sessionStorage.getItem(TOKEN_STORAGE_KEY);
      if (stored) {
        this.token = stored;
      }
    } catch {
      // Falha ao acessar sessionStorage (ex: modo anônimo restritivo)
    }
  }

  /**
   * Define o token JWT ativo e persiste em sessionStorage.
   */
  public setToken(token: string | null): void {
    this.token = token;
    if (typeof window === "undefined" || !window.sessionStorage) return;
    try {
      if (token) {
        window.sessionStorage.setItem(TOKEN_STORAGE_KEY, token);
      } else {
        window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
      }
    } catch {
      // Ignora erro de acesso ao sessionStorage
    }
  }

  /**
   * Retorna o token JWT atual.
   */
  public getToken(): string | null {
    return this.token;
  }

  /**
   * Limpa o token JWT ativo.
   */
  public clearToken(): void {
    this.setToken(null);
  }

  /**
   * Altera a URL base em tempo de execução.
   */
  public setBaseUrl(url: string): void {
    this.baseUrl = url.replace(/\/+$/, "");
  }

  /**
   * Retorna a URL base configurada.
   */
  public getBaseUrl(): string {
    return this.baseUrl;
  }

  /**
   * Registra listener para respostas 401 (token expirado ou inválido).
   */
  public onUnauthorized(callback: AuthFailureCallback): () => void {
    this.unauthorizedListeners.add(callback);
    return () => this.unauthorizedListeners.delete(callback);
  }

  /**
   * Registra listener para respostas 403 (acesso negado).
   */
  public onForbidden(callback: AuthFailureCallback): () => void {
    this.forbiddenListeners.add(callback);
    return () => this.forbiddenListeners.delete(callback);
  }

  /**
   * Constrói a URL completa com parâmetros de consulta (query params).
   */
  private buildUrl(
    path: string,
    params?: Record<string, string | number | boolean | undefined | null>,
  ): string {
    const isAbsolute = path.startsWith("http://") || path.startsWith("https://");
    const cleanPath = path.startsWith("/") ? path : `/${path}`;
    const base = isAbsolute ? path : `${this.baseUrl}${cleanPath}`;

    if (!params) return base;

    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== "") {
        query.append(key, String(value));
      }
    }

    const queryString = query.toString();
    if (!queryString) return base;
    return `${base}${base.includes("?") ? "&" : "?"}${queryString}`;
  }

  /**
   * Constrói os cabeçalhos padrão da requisição.
   */
  private buildHeaders(
    customHeaders?: Record<string, string>,
    isMultipart = false,
    auth = true,
  ): Headers {
    const headers = new Headers();

    if (!isMultipart) {
      headers.set("Content-Type", "application/json");
    }
    headers.set("Accept", "application/json");

    if (auth && this.token) {
      headers.set("Authorization", `Bearer ${this.token}`);
    }

    if (customHeaders) {
      for (const [key, value] of Object.entries(customHeaders)) {
        headers.set(key, value);
      }
    }

    return headers;
  }

  /**
   * Mapeia código HTTP de resposta para ApiErrorCode.
   */
  private mapStatusToErrorCode(status: number): ApiErrorCode {
    switch (status) {
      case 401:
        return "UNAUTHORIZED";
      case 403:
        return "FORBIDDEN";
      case 404:
        return "NOT_FOUND";
      case 409:
        return "CONFLICT";
      case 400:
      case 422:
        return "VALIDATION";
      default:
        return "NETWORK";
    }
  }

  /**
   * Processa a resposta HTTP, tratando erros do backend de forma padronizada.
   */
  private async handleResponse<T>(response: Response, isAuthRequest = true): Promise<T> {
    if (response.status === 204) {
      return undefined as unknown as T;
    }

    const contentType = response.headers.get("content-type") || "";
    const isJson = contentType.includes("application/json");

    if (response.ok) {
      if (isJson) {
        return (await response.json()) as T;
      }
      return (await response.text()) as unknown as T;
    }

    // Processamento de respostas com erro HTTP
    let errorMessage = `Erro HTTP ${response.status}: ${response.statusText}`;
    if (isJson) {
      try {
        const errorBody = (await response.json()) as BackendErrorResponse;
        if (errorBody && errorBody.error) {
          errorMessage = errorBody.error;
        }
      } catch {
        // Fallback para mensagem genérica
      }
    } else {
      try {
        const text = await response.text();
        if (text) errorMessage = text;
      } catch {
        // Fallback
      }
    }

    const errorCode = this.mapStatusToErrorCode(response.status);
    const apiError = new ApiError(errorCode, errorMessage, response.status);

    if (response.status === 401 && isAuthRequest) {
      this.unauthorizedListeners.forEach((fn) => fn(401, apiError));
    } else if (response.status === 403) {
      this.forbiddenListeners.forEach((fn) => fn(403, apiError));
    }

    throw apiError;
  }

  /**
   * Executa uma requisição HTTP via fetch.
   */
  public async request<T>(
    method: string,
    path: string,
    body?: unknown,
    options?: RequestOptions,
  ): Promise<T> {
    const isFormData = typeof FormData !== "undefined" && body instanceof FormData;
    const url = this.buildUrl(path, options?.params);
    const auth = options?.auth !== false;
    const headers = this.buildHeaders(options?.headers, isFormData, auth);

    let requestBody: BodyInit | undefined;
    if (body !== undefined && body !== null) {
      if (isFormData || typeof body === "string" || body instanceof Blob || body instanceof ArrayBuffer) {
        requestBody = body as BodyInit;
      } else {
        requestBody = JSON.stringify(body);
      }
    }

    try {
      const response = await fetch(url, {
        method,
        headers,
        body: requestBody,
        signal: options?.signal,
      });

      return await this.handleResponse<T>(response, auth);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        throw err;
      }
      if (err instanceof DOMException && err.name === "AbortError") {
        throw new ApiError("ABORTED", "Requisição cancelada.", 0);
      }
      const message = err instanceof Error ? err.message : "Falha na comunicação de rede com o servidor.";
      throw new ApiError("NETWORK", `Falha de rede: ${message}`, 0);
    }
  }

  public get<T>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>("GET", path, undefined, options);
  }

  public post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>("POST", path, body, options);
  }

  public put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>("PUT", path, body, options);
  }

  public patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>("PATCH", path, body, options);
  }

  public delete<T>(path: string, options?: RequestOptions): Promise<T> {
    return this.request<T>("DELETE", path, undefined, options);
  }

  /**
   * Suporte a upload de arquivos com acompanhamento de progresso via XMLHttpRequest.
   */
  public upload<T>(
    path: string,
    formData: FormData,
    options?: UploadOptions,
  ): Promise<T> {
    // Se não há callback de progresso e XMLHttpRequest não for obrigatório, usa fetch tradicional
    if (!options?.onProgress) {
      return this.request<T>("POST", path, formData, options);
    }

    const url = this.buildUrl(path, options?.params);
    const auth = options?.auth !== false;

    return new Promise<T>((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", url, true);
      xhr.responseType = "json";

      if (auth && this.token) {
        xhr.setRequestHeader("Authorization", `Bearer ${this.token}`);
      }
      xhr.setRequestHeader("Accept", "application/json");

      if (options?.headers) {
        for (const [k, v] of Object.entries(options.headers)) {
          xhr.setRequestHeader(k, v);
        }
      }

      if (options?.onProgress) {
        xhr.upload.onprogress = (evt: ProgressEvent) => {
          if (evt.lengthComputable && options.onProgress) {
            const p: UploadProgress = {
              loaded: evt.loaded,
              total: evt.total,
            };
            options.onProgress(p);
          }
        };
      }

      if (options?.signal) {
        options.signal.addEventListener("abort", () => {
          xhr.abort();
          reject(new ApiError("ABORTED", "Upload cancelado.", 0));
        });
      }

      xhr.onload = () => {
        const status = xhr.status;
        const responseData = xhr.response;

        if (status >= 200 && status < 300) {
          resolve(responseData as T);
          return;
        }

        let errorMessage = `Erro HTTP ${status}: ${xhr.statusText}`;
        if (responseData && typeof responseData === "object" && "error" in responseData) {
          errorMessage = String(responseData.error);
        }

        const errorCode = this.mapStatusToErrorCode(status);
        const apiError = new ApiError(errorCode, errorMessage, status);

        if (status === 401) {
          this.unauthorizedListeners.forEach((fn) => fn(401, apiError));
        } else if (status === 403) {
          this.forbiddenListeners.forEach((fn) => fn(403, apiError));
        }

        reject(apiError);
      };

      xhr.onerror = () => {
        reject(new ApiError("NETWORK", "Falha de rede durante o upload.", 0));
      };

      xhr.send(formData);
    });
  }
}

/** Instância singleton compartilhada do cliente HTTP configurada com as variáveis da aplicação */
export const httpClient = new HttpClient();
