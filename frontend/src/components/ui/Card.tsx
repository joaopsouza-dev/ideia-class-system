import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <section className={cn("rounded-lg border border-line bg-white p-5 shadow-card", className)}>
      {children}
    </section>
  );
}

/**
 * Assinatura 3 — faixa de telemetria: UMA leitura contínua (painel escuro com grade),
 * no lugar de cartões de estatística idênticos lado a lado.
 */
export function TelemetryStrip({
  items,
  label,
  children,
}: {
  items: Array<{ label: string; value: ReactNode; hint?: string; tone?: "default" | "alerta" | "ciano" }>;
  label: string;
  children?: ReactNode;
}) {
  const tone = { default: "text-white", alerta: "text-rosa", ciano: "text-ciano" };
  return (
    <section aria-label={label} className="grid-tech relative overflow-hidden rounded-lg bg-vacuo px-5 py-6 text-estelar sm:px-8">
      <dl className="relative grid grid-cols-2 gap-y-6 lg:flex lg:items-end">
        {items.map((it, i) => (
          <div
            key={it.label}
            className={cn(
              "min-w-0 px-0 lg:px-7 lg:first:pl-0",
              i > 0 && "lg:border-l lg:border-vacuo-line",
              i % 2 === 1 && "pl-5 sm:pl-8 lg:pl-7",
            )}
          >
            <dt className="lbl text-[10px] text-[#b8c0dc]">{it.label}</dt>
            <dd className={cn("mt-2 text-4xl font-extrabold leading-none tracking-tight tabular-nums", tone[it.tone ?? "default"])}>
              {it.value}
            </dd>
            {it.hint && <p className="mt-2 text-xs text-[#b8c0dc]">{it.hint}</p>}
          </div>
        ))}
        {children}
      </dl>
    </section>
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
          <p className="lbl mb-1.5 text-acao-700">
            {eyebrow}
          </p>
        )}
        <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-[15px] text-ink-soft">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
