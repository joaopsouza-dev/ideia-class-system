import { Link } from "react-router-dom";
import { Logo } from "@/components/brand/Logo";
import { buttonClass } from "@/components/ui/Button";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

export function NotFoundPage({ forbidden = false }: { forbidden?: boolean }) {
  useDocumentTitle(forbidden ? "Acesso negado" : "Página não encontrada");
  return (
    <div className="starfield grid min-h-dvh place-items-center bg-vacuo px-4 text-center text-white">
      <div>
        <Logo className="mb-8" />
        <p className="font-ui text-sm font-semibold uppercase tracking-[0.3em] text-ciano">
          {forbidden ? "403" : "404"}
        </p>
        <h1 className="mt-2 text-3xl">{forbidden ? "Acesso não autorizado" : "Fora da órbita"}</h1>
        <p className="mx-auto mt-3 max-w-md text-sm text-white/70">
          {forbidden
            ? "Seu perfil não tem permissão para acessar esta página."
            : "A página que você procura não existe ou foi movida."}
        </p>
        <Link to="/" className={buttonClass("primary", "lg", "mt-8")}>
          Voltar ao início
        </Link>
      </div>
    </div>
  );
}
