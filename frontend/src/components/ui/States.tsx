import type { ReactNode } from "react";
import { AlertCircle, Loader2, type LucideIcon } from "lucide-react";
import { Button } from "./Button";
import { cn } from "@/utils/cn";

export function Spinner({ label = "Carregando…", className }: { label?: string; className?: string }) {
  return (
    <div role="status" className={cn("flex items-center justify-center gap-3 py-16 text-ink-soft", className)}>
      <Loader2 className="size-5 animate-spin text-magenta" aria-hidden />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("animate-pulse rounded-lg bg-estrato/8", className)} />;
}

export function CardGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div role="status" aria-label="Carregando conteúdos" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="rounded-2xl border border-line bg-white p-5">
          <Skeleton className="mb-4 h-10 w-10" />
          <Skeleton className="mb-2 h-4 w-3/4" />
          <Skeleton className="mb-5 h-3 w-1/2" />
          <Skeleton className="h-3 w-full" />
        </div>
      ))}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: {
  icon: LucideIcon;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-dashed border-titanio/60 bg-white px-6 py-14 text-center">
      <span className="mb-4 grid size-14 place-items-center rounded-2xl bg-estrato/8 text-estrato">
        <Icon className="size-7" aria-hidden />
      </span>
      <h3 className="text-base">{title}</h3>
      {description && <p className="mt-1.5 max-w-md text-sm text-ink-soft">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center rounded-2xl border border-danger/30 bg-danger-50 px-6 py-12 text-center"
    >
      <AlertCircle className="mb-3 size-8 text-danger" aria-hidden />
      <h3 className="text-base text-danger">Não foi possível carregar</h3>
      <p className="mt-1.5 max-w-md text-sm text-ink-soft">{message}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-5" onClick={onRetry}>
          Tentar novamente
        </Button>
      )}
    </div>
  );
}
