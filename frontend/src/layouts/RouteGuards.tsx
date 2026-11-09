import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/context/AuthContext";
import { Spinner } from "@/components/ui/States";
import type { Perfil } from "@/types";

/**
 * Guardas de rota — conveniência de UX apenas. A autorização verdadeira é
 * sempre validada pelo backend (RLS); estas guardas só evitam telas inúteis.
 */
export function RequireAuth() {
  const { sessao, restoring } = useAuth();
  const location = useLocation();
  if (restoring) return <Spinner label="Verificando sessão…" className="min-h-dvh" />;
  if (!sessao) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

export function RequireRole({ perfis }: { perfis: Perfil[] }) {
  const { sessao } = useAuth();
  if (!sessao || !perfis.includes(sessao.usuario.perfil)) return <Navigate to="/acesso-negado" replace />;
  return <Outlet />;
}

export function GuestOnly() {
  const { sessao, restoring } = useAuth();
  if (restoring) return <Spinner label="Carregando…" className="min-h-dvh" />;
  if (sessao) return <Navigate to="/" replace />;
  return <Outlet />;
}
