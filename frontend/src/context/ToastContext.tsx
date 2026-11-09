import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from "lucide-react";
import { cn } from "@/utils/cn";

type ToastKind = "success" | "error" | "info" | "warning";
interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  message?: string;
}

interface ToastApi {
  notify: (kind: ToastKind, title: string, message?: string) => void;
  success: (title: string, message?: string) => void;
  error: (title: string, message?: string) => void;
  info: (title: string, message?: string) => void;
  warning: (title: string, message?: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const styles: Record<ToastKind, { box: string; icon: ReactNode }> = {
  success: { box: "border-ok/40", icon: <CheckCircle2 className="size-5 text-ok" aria-hidden /> },
  error: { box: "border-danger/40", icon: <XCircle className="size-5 text-danger" aria-hidden /> },
  warning: { box: "border-warn/40", icon: <AlertTriangle className="size-5 text-warn" aria-hidden /> },
  info: { box: "border-ciano-700/40", icon: <Info className="size-5 text-ciano-700" aria-hidden /> },
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const counter = useRef(0);

  const dismiss = useCallback((id: number) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const notify = useCallback(
    (kind: ToastKind, title: string, message?: string) => {
      const id = ++counter.current;
      setToasts((t) => [...t.slice(-3), { id, kind, title, message }]);
      setTimeout(() => dismiss(id), kind === "error" ? 7000 : 4500);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      notify,
      success: (t, m) => notify("success", t, m),
      error: (t, m) => notify("error", t, m),
      info: (t, m) => notify("info", t, m),
      warning: (t, m) => notify("warning", t, m),
    }),
    [notify],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 bottom-4 z-[100] flex flex-col items-center gap-2 px-4 sm:items-end sm:px-6"
        role="region"
        aria-label="Notificações"
        aria-live="polite"
      >
        {toasts.map((t) => (
          <div
            key={t.id}
            role={t.kind === "error" ? "alert" : "status"}
            className={cn(
              "pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border bg-white p-3.5 shadow-pop",
              styles[t.kind].box,
            )}
          >
            <span className="mt-0.5 shrink-0">{styles[t.kind].icon}</span>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-ink">{t.title}</p>
              {t.message && <p className="mt-0.5 text-sm text-ink-soft">{t.message}</p>}
            </div>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              className="rounded p-1 text-ink-soft hover:bg-estelar hover:text-ink"
              aria-label="Fechar notificação"
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast fora do ToastProvider");
  return ctx;
}
