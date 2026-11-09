import { useEffect, useRef, useState } from "react";
import type { TipoAcao } from "@/types";

/**
 * Proteção DISSUASIVA no navegador.
 *
 * Detecta e bloqueia atalhos/eventos comuns (print, salvar, copiar, menu de contexto,
 * arrastar, impressão) e oculta o conteúdo quando a janela perde o foco.
 *
 * LIMITES (devem ser comunicados à equipe/banca): nada aqui é segurança absoluta.
 * Fotos da tela, gravação externa, ferramentas do sistema operacional e usuários que
 * desativam JavaScript não podem ser impedidos. A proteção efetiva (URLs assinadas e
 * expiráveis, autorização, logs imutáveis) é responsabilidade do backend. O que este
 * hook entrega é dificultar o uso casual e GERAR REGISTROS para rastreabilidade.
 */
export type AttemptHandler = (tipo: TipoAcao, detalhe: string) => void;

interface Options {
  enabled?: boolean;
  /** elemento (ou ancestral) onde cliques/arrastos/menu de contexto são bloqueados */
  surface: React.RefObject<HTMLElement | null>;
  onAttempt: AttemptHandler;
  /** liga a ocultação ao perder o foco (padrão: true) */
  obscureOnBlur?: boolean;
}

export function useContentProtection({ enabled = true, surface, onAttempt, obscureOnBlur = true }: Options) {
  const [obscured, setObscured] = useState(false);
  const attemptRef = useRef(onAttempt);
  attemptRef.current = onAttempt;
  const last = useRef<Record<string, number>>({});

  useEffect(() => {
    if (!enabled) return;

    const report = (tipo: TipoAcao, detalhe: string) => {
      const now = Date.now();
      const key = `${tipo}:${detalhe}`;
      if (now - (last.current[key] ?? 0) < 2500) return; // evita enxurrada de registros
      last.current[key] = now;
      attemptRef.current(tipo, detalhe);
    };
    const inSurface = (t: EventTarget | null) =>
      !!surface.current && t instanceof Node && surface.current.contains(t);

    const onKeyDown = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      const key = e.key.toLowerCase();
      if (e.key === "PrintScreen") {
        // Não impede a captura do SO; apenas limpa a área de transferência e registra.
        navigator.clipboard?.writeText("").catch(() => undefined);
        setObscured(true);
        setTimeout(() => setObscured(false), 1200);
        report("TENTATIVA_CAPTURA", "Tecla PrintScreen");
      } else if (e.metaKey && e.shiftKey && ["Digit3", "Digit4", "Digit5"].includes(e.code)) {
        report("TENTATIVA_CAPTURA", "Atalho de captura do macOS");
      } else if (e.metaKey && e.shiftKey && e.code === "KeyS") {
        e.preventDefault();
        report("TENTATIVA_CAPTURA", "Atalho de recorte (Win+Shift+S)");
      } else if (mod && key === "p") {
        e.preventDefault();
        report("TENTATIVA_IMPRESSAO", "Atalho de impressão");
      } else if (mod && key === "s") {
        e.preventDefault();
        report("TENTATIVA_DOWNLOAD", "Atalho de salvar página");
      } else if (mod && ["c", "x", "a"].includes(key) && inSurface(document.activeElement ?? e.target)) {
        e.preventDefault();
        report("TENTATIVA_COPIA", "Atalho de cópia/seleção");
      }
    };
    const onContext = (e: MouseEvent) => {
      if (!inSurface(e.target)) return;
      e.preventDefault();
      report("TENTATIVA_DOWNLOAD", "Menu de contexto (salvar como)");
    };
    const onCopy = (e: ClipboardEvent) => {
      if (!inSurface(e.target) && !inSurface(document.activeElement)) return;
      e.preventDefault();
      report("TENTATIVA_COPIA", "Evento de cópia");
    };
    const onDrag = (e: DragEvent) => {
      if (inSurface(e.target)) e.preventDefault();
    };
    const onBeforePrint = () => report("TENTATIVA_IMPRESSAO", "Impressão via navegador");
    const hide = () => {
      if (!obscureOnBlur) return;
      setObscured(true);
      report("PERDA_DE_FOCO", "Janela perdeu o foco");
    };
    const show = () => setObscured(false);
    const onVisibility = () => (document.hidden ? hide() : show());

    document.addEventListener("keydown", onKeyDown, true);
    document.addEventListener("contextmenu", onContext, true);
    document.addEventListener("copy", onCopy, true);
    document.addEventListener("cut", onCopy, true);
    document.addEventListener("dragstart", onDrag, true);
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("beforeprint", onBeforePrint);
    window.addEventListener("blur", hide);
    window.addEventListener("focus", show);
    return () => {
      document.removeEventListener("keydown", onKeyDown, true);
      document.removeEventListener("contextmenu", onContext, true);
      document.removeEventListener("copy", onCopy, true);
      document.removeEventListener("cut", onCopy, true);
      document.removeEventListener("dragstart", onDrag, true);
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("beforeprint", onBeforePrint);
      window.removeEventListener("blur", hide);
      window.removeEventListener("focus", show);
    };
  }, [enabled, surface, obscureOnBlur]);

  return { obscured };
}
