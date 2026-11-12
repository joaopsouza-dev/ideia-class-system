import type { AuthService } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS, type LoginResponseDTO, TOKEN_STORAGE_KEY } from "../api";
import type { Perfil, RegistroUsuarioInput, Sessao, Usuario } from "@/types";

export const SESSION_STORAGE_KEY = "ideiaspace:auth_session";

/**
 * Converte o perfil retornado pelo backend para o Perfil aceito pela interface.
 * O backend utiliza "DOCENTE", enquanto a interface do frontend espera "PROFESSOR".
 */
function normalizarPerfil(perfilBackend: string): Perfil {
  if (perfilBackend === "DOCENTE") return "PROFESSOR";
  if (perfilBackend === "ADMIN") return "ADMIN";
  return "ALUNO";
}

/**
 * Adapter HTTP real para AuthService.
 * Comunica-se exclusivamente com os endpoints existentes no backend (/api/v1/auth/login).
 */
export const httpAuthService: AuthService = {
  async login(email: string, senha: string): Promise<Sessao> {
    const data = await httpClient.post<LoginResponseDTO>(
      API_ENDPOINTS.AUTH.LOGIN,
      {
        email: email.trim(),
        senha,
      },
      { auth: false },
    );

    if (!data || !data.token || !data.usuario) {
      throw new ApiError("NETWORK", "Resposta inválida recebida do servidor de autenticação.", 500);
    }

    // Armazena o JWT no cliente HTTP centralizado e no storage
    httpClient.setToken(data.token);

    const perfil = normalizarPerfil(data.usuario.perfil);

    const usuario: Usuario = {
      id: data.usuario.id,
      nomeCompleto: data.usuario.nomeCompleto,
      email: data.usuario.email,
      perfil,
    };

    const sessao: Sessao = {
      usuario,
      sessionId: `SES-${Math.random().toString(36).substring(2, 8).toUpperCase()}`,
      iniciadaEm: new Date().toISOString(),
      modo: "API",
      token: data.token,
    };

    try {
      if (typeof window !== "undefined" && window.sessionStorage) {
        window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessao));
      }
    } catch {
      // Ignora erro de acesso ao sessionStorage
    }

    return sessao;
  },

  async restore(): Promise<Sessao | null> {
    try {
      if (typeof window === "undefined" || !window.sessionStorage) return null;

      const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);
      if (!raw) return null;

      const sessao = JSON.parse(raw) as Sessao;
      const storedToken =
        sessao.token || httpClient.getToken() || window.sessionStorage.getItem(TOKEN_STORAGE_KEY);

      // Não considera uma sessão válida se não houver token ou usuário consistente
      if (!storedToken || !sessao.usuario || !sessao.usuario.id || !sessao.usuario.email) {
        httpClient.clearToken();
        window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }

      // Restaura o token no cliente HTTP
      httpClient.setToken(storedToken);

      // Garante que o perfil esteja normalizado
      sessao.usuario.perfil = normalizarPerfil(sessao.usuario.perfil);
      sessao.token = storedToken;

      return sessao;
    } catch {
      httpClient.clearToken();
      if (typeof window !== "undefined" && window.sessionStorage) {
        window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
      }
      return null;
    }
  },

  async logout(_sessao: Sessao): Promise<void> {
    httpClient.clearToken();
    try {
      if (typeof window !== "undefined" && window.sessionStorage) {
        window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
      }
    } catch {
      // Ignora erro
    }
  },

  async register(_input: RegistroUsuarioInput): Promise<void> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "O cadastro público de usuários ainda não está disponível no backend. O acesso é liberado mediante provisionamento administrativo.",
      501,
    );
  },

  async requestPasswordReset(_email: string): Promise<void> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "A recuperação de senha ainda não está disponível no backend. Entre em contato com a administração para redefinir suas credenciais.",
      501,
    );
  },
};
