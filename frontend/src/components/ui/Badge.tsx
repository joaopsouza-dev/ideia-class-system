import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

type Tone = "neutral" | "magenta" | "ciano" | "nebulosa" | "ok" | "warn" | "danger" | "dark";

const tones: Record<Tone, string> = {
  neutral: "bg-estelar text-ink-soft ring-line",
  magenta: "bg-magenta-50 text-acao-700 ring-acao-700/40",
  ciano: "bg-ciano-50 text-ciano-700 ring-ciano/40",
  nebulosa: "bg-nebulosa-50 text-nebulosa ring-nebulosa/25",
  ok: "bg-ok-50 text-ok ring-ok/25",
  warn: "bg-warn-50 text-warn ring-warn/25",
  danger: "bg-danger-50 text-danger ring-danger/25",
  dark: "bg-estrato text-white ring-estrato",
};

export function Badge({
  tone = "neutral",
  icon,
  children,
  className,
}: {
  tone?: Tone;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 whitespace-nowrap rounded-[3px] px-2 py-0.5 font-ui text-[10.5px] font-semibold uppercase tracking-[0.1em] ring-1 ring-inset",
        tones[tone],
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}
