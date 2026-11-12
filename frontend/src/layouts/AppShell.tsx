import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { FlaskConical, LogOut, Menu, ShieldCheck, X } from "lucide-react";
import { Logo } from "@/components/brand/Logo";
import { IconButton } from "@/components/ui/Button";
import { useAuth, useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { isDemoMode, isMockAuth } from "@/services";
import { cn } from "@/utils/cn";
import { initials } from "@/utils/format";
import { NAV, PERFIL_LABEL } from "./nav";

export function AppShell() {
  const sessao = useSessao();
  const { logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const u = sessao.usuario;

  useEffect(() => setOpen(false), [pathname]);

  async function handleLogout() {
    setLeaving(true);
    await logout();
    toast.info("Sessão encerrada", "Você saiu da plataforma com segurança.");
    navigate("/login", { replace: true });
  }

  const sidebar = (
    <div className="flex h-full flex-col bg-vacuo text-white">
      <div className="flex h-16 items-center justify-between px-5">
        <Logo />
        <button
          type="button"
          className="rounded-lg p-2 text-white/70 hover:bg-white/10 lg:hidden"
          onClick={() => setOpen(false)}
          aria-label="Fechar menu"
        >
          <X className="size-5" aria-hidden />
        </button>
      </div>
      <nav aria-label="Principal" className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
        {NAV[u.perfil].map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              cn(
                "flex items-center gap-3 rounded px-3.5 py-2.5 font-ui text-sm font-medium transition-colors",
                isActive
                  ? "bg-estrato text-white shadow-[inset_3px_0_0_var(--color-magenta)]"
                  : "text-[#b8c0dc] hover:bg-white/10 hover:text-white",
              )
            }
          >
            <Icon className="size-[18px]" aria-hidden />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="m-3 rounded border border-vacuo-line p-3.5">
        <div className="flex items-center gap-2 font-ui text-xs font-semibold text-ciano">
          <ShieldCheck className="size-4" aria-hidden />
          Conteúdo protegido
        </div>
        <p className="mt-1.5 text-xs leading-relaxed text-white/65">
          Seus acessos são identificados e registrados para proteger a propriedade intelectual.
        </p>
      </div>
      <div className="border-t border-white/10 p-3">
        <div className="flex items-center gap-3 rounded-lg p-2">
          <span className="grid size-10 shrink-0 place-items-center rounded-full bg-estrato-700 font-ui text-sm font-semibold ring-2 ring-ciano/60">
            {initials(u.nomeCompleto)}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{u.nomeCompleto}</p>
            <p className="font-ui text-xs text-white/60">{PERFIL_LABEL[u.perfil]}</p>
          </div>
          <IconButton
            label="Sair"
            onClick={handleLogout}
            disabled={leaving}
            className="text-white/75 hover:bg-white/10 hover:text-white"
          >
            <LogOut className="size-[18px]" aria-hidden />
          </IconButton>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[17rem_1fr]">
      <a
        href="#conteudo"
        className="sr-only z-[200] rounded-lg bg-white px-4 py-2 font-ui text-sm focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Pular para o conteúdo
      </a>
      <aside className="sticky top-0 hidden h-dvh lg:block">{sidebar}</aside>

      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-vacuo/70" onClick={() => setOpen(false)} aria-hidden />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85vw]">{sidebar}</aside>
        </div>
      )}

      <div className="flex min-w-0 flex-col">
        {isDemoMode && (
          <div
            role="note"
            className="flex items-center justify-center gap-2 bg-estrato px-4 py-1.5 text-center font-ui text-xs text-white"
          >
            <FlaskConical className="size-3.5 shrink-0 text-ciano" aria-hidden />
            <span>
              {isMockAuth ? (
                <>
                  <strong className="text-ciano">Modo demonstração</strong> — autenticação e dados acadêmicos simulados no navegador.
                </>
              ) : (
                <>
                  <strong className="text-ciano">Ambiente híbrido</strong> — autenticação real via backend; turmas, materiais e logs com dados simulados nesta etapa.
                </>
              )}
            </span>
          </div>
        )}
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-line bg-white/90 px-4 backdrop-blur lg:hidden">
          <IconButton label="Abrir menu" onClick={() => setOpen(true)}>
            <Menu className="size-5" aria-hidden />
          </IconButton>
          <Logo tone="dark" />
        </header>
        <main id="conteudo" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-8 sm:py-8">
          <Outlet />
        </main>
        <footer className="border-t border-line px-4 py-4 text-center font-ui text-xs text-ink-soft sm:px-8">
          Ideia Space Educação Espacial · Repositório Digital Protegido · Porto Digital Squad 22
        </footer>
      </div>
    </div>
  );
}
