import type { LucideIcon } from "lucide-react";
import { cn } from "@/utils/cn";

const SIZE = { sm: 16, md: 20, lg: 24 } as const;

/**
 * Wrapper único de ícones (identidade "Telemetria"): traço 1,75, grade de 24 px,
 * `currentColor`, decorativo por padrão (aria-hidden). Use `label` apenas quando o
 * ícone for a única informação do controle.
 */
export function Icon({
  icon: Glyph,
  size = "md",
  label,
  className,
}: {
  icon: LucideIcon;
  size?: keyof typeof SIZE;
  label?: string;
  className?: string;
}) {
  return (
    <Glyph
      width={SIZE[size]}
      height={SIZE[size]}
      strokeWidth={1.75}
      className={cn("shrink-0", className)}
      aria-hidden={label ? undefined : true}
      aria-label={label}
      role={label ? "img" : undefined}
    />
  );
}
