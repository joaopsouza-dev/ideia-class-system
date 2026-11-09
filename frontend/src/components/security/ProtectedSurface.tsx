import { useCallback, useEffect, useRef, type ReactNode } from "react";
import { EyeOff } from "lucide-react";
import { useContentProtection } from "@/hooks/useContentProtection";
import { useToast } from "@/context/ToastContext";
import { services } from "@/services";
import type { Sessao, TipoAcao } from "@/types";
import { cn } from "@/utils/cn";
import { Watermark } from "./Watermark";

interface Props {
  sessao: Sessao;
  conteudoId: string;
  on?: "light" | "dark";
  className?: string;
  children: ReactNode;
  /** chamado quando o conteúdo é ocultado/exibido (ex.: pausar o vídeo) */
  onObscuredChange?: (obscured: boolean) => void;
}

const MESSAGES: Partial<Record<TipoAcao, string>> = {
  TENTATIVA_CAPTURA: "Capturas de tela não são permitidas neste conteúdo.",
  TENTATIVA_IMPRESSAO: "A impressão não é permitida neste conteúdo.",
  TENTATIVA_DOWNLOAD: "O download deste conteúdo não é permitido.",
  TENTATIVA_COPIA: "A cópia deste conteúdo não é permitida.",
  MARCA_DAGUA_ALTERADA: "A marca d'água foi restaurada.",
};

/**
 * Contêiner de conteúdo protegido: marca d'água dinâmica + proteção dissuasiva +
 * registro das tentativas. Vale para PDF e vídeo (incluindo tela cheia, pois o
 * próprio contêiner é o elemento enviado ao modo fullscreen).
 */
export function ProtectedSurface({ sessao, conteudoId, on = "light", className, children, onObscuredChange }: Props) {
  const ref = useRef<HTMLDivElement>(null);
  const toast = useToast();

  const onAttempt = useCallback(
    (tipo: TipoAcao, detalhe: string) => {
      services.accessLog.record(sessao, { tipoAcao: tipo, conteudoId, detalhe }).catch(() => undefined);
      const msg = MESSAGES[tipo];
      if (msg) toast.warning("Ação não permitida", `${msg} A tentativa foi registrada.`);
    },
    [sessao, conteudoId, toast],
  );

  const { obscured } = useContentProtection({ surface: ref, onAttempt });
  useEffect(() => {
    onObscuredChange?.(obscured);
  }, [obscured, onObscuredChange]);

  // Restaura a marca d'água se alguém removê-la/escondê-la pelo DOM. Reanexa o MESMO nó
  // (remontar via React falharia, pois o nó já não pertence ao pai esperado).
  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const node = root.querySelector<HTMLElement>('[data-watermark="true"]');
    if (!node) return;
    let savedCss = node.style.cssText;
    const check = () => {
      const detached = node.parentElement !== root;
      const cs = getComputedStyle(node);
      const hidden =
        node.hidden || cs.display === "none" || cs.visibility === "hidden" || Number(cs.opacity) < 0.5;
      if (!detached && !hidden) {
        savedCss = node.style.cssText;
        return;
      }
      if (detached) root.appendChild(node);
      node.hidden = false;
      node.style.cssText = savedCss;
      onAttempt("MARCA_DAGUA_ALTERADA", "Marca d'água removida ou ocultada via DOM");
    };
    const mo = new MutationObserver(check);
    mo.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ["style", "class", "hidden"] });
    return () => mo.disconnect();
  }, [onAttempt]);

  return (
    <div ref={ref} className={cn("protected-surface relative isolate overflow-hidden", className)}>
      {children}
      <Watermark sessao={sessao} on={on} />
      {obscured && (
        <div
          role="alert"
          className="absolute inset-0 z-40 flex flex-col items-center justify-center gap-3 bg-vacuo text-center text-white"
        >
          <EyeOff className="size-10 text-ciano" aria-hidden />
          <p className="max-w-xs px-6 text-sm">
            Conteúdo ocultado porque a janela perdeu o foco. Volte para esta aba para continuar.
          </p>
        </div>
      )}
    </div>
  );
}
