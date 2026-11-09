import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { services } from "@/services";
import type { Sessao } from "@/types";

interface AuthContextValue {
  sessao: Sessao | null;
  /** true enquanto tenta restaurar a sessão ao abrir o app */
  restoring: boolean;
  login: (email: string, senha: string) => Promise<Sessao>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let alive = true;
    services.auth
      .restore()
      .then((s) => alive && setSessao(s))
      .catch(() => undefined)
      .finally(() => alive && setRestoring(false));
    return () => {
      alive = false;
    };
  }, []);

  const login = useCallback(async (email: string, senha: string) => {
    const s = await services.auth.login(email, senha);
    setSessao(s);
    return s;
  }, []);

  const logout = useCallback(async () => {
    if (sessao) await services.auth.logout(sessao).catch(() => undefined);
    setSessao(null);
  }, [sessao]);

  const value = useMemo(() => ({ sessao, restoring, login, logout }), [sessao, restoring, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth fora do AuthProvider");
  return ctx;
}

/** Sessão garantida (usar apenas dentro de rotas protegidas). */
export function useSessao(): Sessao {
  const { sessao } = useAuth();
  if (!sessao) throw new Error("Sessão ausente");
  return sessao;
}
