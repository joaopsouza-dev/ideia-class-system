import type { ReactNode } from "react";
import { FlaskConical } from "lucide-react";
import { Logo } from "@/components/brand/Logo";
import { isDemoMode } from "@/services";

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="starfield relative grid min-h-dvh place-items-center overflow-hidden bg-vacuo px-4 py-10">
      <div
        aria-hidden
        className="pointer-events-none absolute -right-40 -top-40 size-[34rem] rounded-full bg-nebulosa/30 blur-3xl"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-48 -left-32 size-[30rem] rounded-full bg-magenta/20 blur-3xl"
      />
      <div className="relative w-full max-w-md">
        <div className="mb-6 flex justify-center">
          <Logo className="scale-125" />
        </div>
        <div className="rounded-2xl bg-white p-6 shadow-pop sm:p-8">
          <h1 className="text-2xl">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-ink-soft">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>
        {isDemoMode && (
          <p className="mt-4 flex items-center justify-center gap-2 text-center font-ui text-xs text-white/70">
            <FlaskConical className="size-3.5 text-ciano" aria-hidden />
            Modo demonstração: autenticação simulada, sem segurança real.
          </p>
        )}
        <p className="mt-6 text-center text-xs text-white/50">
          Ideia Space Educação Espacial · Porto Digital Squad 22
        </p>
      </div>
    </div>
  );
}
