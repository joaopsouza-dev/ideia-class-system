import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/utils/cn";

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <section className={cn("rounded-2xl border border-line bg-white p-5 shadow-card", className)}>
      {children}
    </section>
  );
}

/** "Cartão de telemetria" — métrica com rótulo em caixa alta (aplicação indicada no brandbook). */
export function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  accent = "magenta",
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  icon: LucideIcon;
  accent?: "magenta" | "ciano" | "nebulosa" | "estrato";
}) {
  const bar = {
    magenta: "bg-magenta",
    ciano: "bg-ciano",
    nebulosa: "bg-nebulosa",
    estrato: "bg-estrato",
  }[accent];
  return (
    <div className="relative overflow-hidden rounded-2xl border border-line bg-white p-5 shadow-card">
      <span className={cn("absolute inset-y-0 left-0 w-1", bar)} aria-hidden />
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-ui text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-soft">{label}</p>
          <p className="mt-2 text-3xl font-bold tabular-nums text-ink">{value}</p>
          {hint && <p className="mt-1 text-xs text-ink-soft">{hint}</p>}
        </div>
        <Icon className="size-6 text-titanio" aria-hidden />
      </div>
    </div>
  );
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        {eyebrow && (
          <p className="mb-1 font-ui text-[11px] font-semibold uppercase tracking-[0.18em] text-magenta">
            {eyebrow}
          </p>
        )}
        <h1 className="text-2xl sm:text-3xl">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm text-ink-soft">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
