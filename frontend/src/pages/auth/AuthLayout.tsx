import type { ReactNode } from "react";
import { FlaskConical, Lock } from "lucide-react";
import { Logo } from "@/components/brand/Logo";
import { isDemoMode, isMockAuth } from "@/services";

/** Esquema orbital decorativo (assinatura "trilha orbital" + grade técnica). */
function OrbitScheme() {
  return (
    <svg aria-hidden viewBox="0 0 1280 800" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 size-full">
      <g fill="none" stroke="#8E9EAB">
        <ellipse cx="400" cy="250" rx="520" ry="230" transform="rotate(-16 400 250)" strokeOpacity=".5" />
        <ellipse cx="400" cy="250" rx="360" ry="160" transform="rotate(-16 400 250)" strokeOpacity=".3" />
        <ellipse cx="400" cy="250" rx="200" ry="88" transform="rotate(-16 400 250)" strokeOpacity=".2" />
      </g>
      <foreignObject x="276" y="218" width="192" height="80">
        <div className="flex size-full items-center justify-center">
          <Logo className="h-20 w-48" />
        </div>
      </foreignObject>
      <circle cx="838" cy="132" r="7" fill="#00CFE5" />
    </svg>
  );
}

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="grid-tech relative min-h-dvh overflow-hidden bg-vacuo text-estelar">
      <div className="hidden lg:block">
        <OrbitScheme />
      </div>
      <div className="relative mx-auto flex min-h-dvh w-full max-w-7xl flex-col px-5 py-8 sm:px-10 lg:flex-row lg:items-center lg:justify-between lg:gap-12 lg:px-16">
        <div className="lg:max-w-xl lg:self-stretch lg:py-6">
          <Logo className="origin-left scale-110 lg:hidden" />
          <div className="mt-10 hidden lg:mt-auto lg:block lg:translate-y-8 lg:pt-[28dvh]">
            <p className="lbl text-ciano">Repositório digital protegido</p>
            <p className="mt-4 text-5xl font-extrabold leading-[1.1] tracking-tight">
              Conhecimento real, acesso identificado.
            </p>
            <p className="mt-4 text-base leading-relaxed text-[#b8c0dc]">
              Apostilas e videoaulas da sua turma, em um só lugar. Cada acesso leva o seu nome.
            </p>
          </div>
        </div>

        <div className="relative mt-8 w-full max-w-md self-center lg:mt-0 lg:w-[26rem] lg:flex-none">
          <div className="rounded-lg bg-white p-6 text-ink shadow-pop sm:p-9">
            <p className="lbl text-acao-700">Repositório digital protegido</p>
            <h1 className="mt-2 text-[1.75rem] font-bold">{title}</h1>
            {subtitle && <p className="mt-1.5 text-sm text-ink-soft">{subtitle}</p>}
            <div className="mt-6">{children}</div>
            <div className="mt-6 flex items-start gap-2.5 border-t border-line pt-4 text-[12.5px] leading-relaxed text-ink-soft">
              <Lock className="mt-0.5 size-[18px] shrink-0 text-ciano-700" strokeWidth={1.75} aria-hidden />
              <p>
                Conteúdo protegido: marca d’água com seu nome e registro de acessos.
                {isDemoMode && (
                  <>
                    {" "}
                    {isMockAuth ? (
                      <>
                        <strong className="text-ink">Modo demonstração</strong> — autenticação simulada.
                      </>
                    ) : (
                      <>
                        <strong className="text-ink">Ambiente híbrido</strong> — autenticação real via backend (dados acadêmicos simulados).
                      </>
                    )}
                  </>
                )}
              </p>
            </div>
          </div>
          <p className="mt-5 flex items-center justify-center gap-2 text-center text-xs text-[#8e9eab]">
            {isDemoMode && <FlaskConical className="size-3.5 text-ciano" aria-hidden />}
            Ideia Space Educação Espacial · Porto Digital Squad 22
          </p>
        </div>
      </div>
    </div>
  );
}
