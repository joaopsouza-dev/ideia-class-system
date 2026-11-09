import { useCallback, useEffect, useRef, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Maximize,
  Minimize,
  ScanLine,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import { IconButton, Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/States";
import { ProtectedSurface } from "@/components/security/ProtectedSurface";
import type { Sessao } from "@/types";
import { cn } from "@/utils/cn";

const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

interface Props {
  url: string;
  sessao: Sessao;
  conteudoId: string;
  initialPage?: number;
  onPageChange?: (page: number, total: number) => void;
  onLoaded?: (total: number) => void;
}

/**
 * Visualizador de PDF próprio (pdf.js). Desenha cada página em <canvas> — não usa
 * <iframe>/<embed> (que trariam o botão nativo de download) e não gera camada de
 * texto selecionável. A URL deve ser temporária/assinada e fornecida pelo backend.
 */
export function PdfViewer({ url, sessao, conteudoId, initialPage = 1, onPageChange, onLoaded }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [doc, setDoc] = useState<PDFDocumentProxy | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [errorMsg, setErrorMsg] = useState("");
  const [page, setPage] = useState(initialPage);
  const [zoomIdx, setZoomIdx] = useState(2); // 100% = ajustar à largura
  const [width, setWidth] = useState(0);
  const [rendering, setRendering] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const total = doc?.numPages ?? 0;

  // carrega o documento
  useEffect(() => {
    let cancelled = false;
    let loadingTask: PDFDocumentLoadingTask | null = null;
    setStatus("loading");
    setDoc(null);
    (async () => {
      try {
        const pdfjs = await import("pdfjs-dist");
        const worker = (await import("pdfjs-dist/build/pdf.worker.min.mjs?url")).default;
        pdfjs.GlobalWorkerOptions.workerSrc = worker;
        loadingTask = pdfjs.getDocument({ url });
        const loaded = await loadingTask.promise;
        if (cancelled) return;
        setDoc(loaded);
        setPage((p) => Math.min(Math.max(1, p), loaded.numPages));
        setStatus("ready");
        onLoaded?.(loaded.numPages);
      } catch (e) {
        if (cancelled) return;
        setErrorMsg(e instanceof Error ? e.message : "Falha ao abrir o documento.");
        setStatus("error");
      }
    })();
    return () => {
      cancelled = true;
      void loadingTask?.destroy();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [url, attempt]);

  // largura disponível
  useEffect(() => {
    const el = stageRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  // desenha a página
  useEffect(() => {
    if (!doc || !width) return;
    let task: RenderTask | null = null;
    let cancelled = false;
    setRendering(true);
    (async () => {
      try {
        const p = await doc.getPage(page);
        if (cancelled) return;
        const base = p.getViewport({ scale: 1 });
        const fit = Math.max(width - 32, 200) / base.width;
        const viewport = p.getViewport({ scale: fit * ZOOMS[zoomIdx] });
        const canvas = canvasRef.current;
        if (!canvas) return;
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = Math.floor(viewport.width * dpr);
        canvas.height = Math.floor(viewport.height * dpr);
        canvas.style.width = `${Math.floor(viewport.width)}px`;
        canvas.style.height = `${Math.floor(viewport.height)}px`;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        task = p.render({
          canvas,
          canvasContext: ctx,
          viewport,
          transform: dpr !== 1 ? [dpr, 0, 0, dpr, 0, 0] : undefined,
        });
        await task.promise;
        if (!cancelled) setRendering(false);
      } catch (e) {
        // RenderingCancelledException é esperado ao trocar de página/zoom rapidamente
        if (!cancelled && !(e instanceof Error && e.name === "RenderingCancelledException")) {
          setErrorMsg("Não foi possível exibir esta página.");
          setStatus("error");
        }
      }
    })();
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, page, zoomIdx, width]);

  useEffect(() => {
    if (total) onPageChange?.(page, total);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, total]);

  const go = useCallback(
    (n: number) => setPage((p) => (total ? Math.min(Math.max(1, n), total) : p)),
    [total],
  );

  useEffect(() => {
    const onFs = () => setFullscreen(document.fullscreenElement === wrapRef.current);
    document.addEventListener("fullscreenchange", onFs);
    return () => document.removeEventListener("fullscreenchange", onFs);
  }, []);

  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void wrapRef.current?.requestFullscreen?.();
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.target as HTMLElement).tagName === "INPUT") return;
    if (["ArrowRight", "PageDown"].includes(e.key)) go(page + 1);
    else if (["ArrowLeft", "PageUp"].includes(e.key)) go(page - 1);
    else if (e.key === "Home") go(1);
    else if (e.key === "End") go(total);
    else if (e.key === "+" || e.key === "=") setZoomIdx((z) => Math.min(z + 1, ZOOMS.length - 1));
    else if (e.key === "-") setZoomIdx((z) => Math.max(z - 1, 0));
    else return;
    e.preventDefault();
  }

  return (
    <div
      ref={wrapRef}
      tabIndex={0}
      onKeyDown={onKeyDown}
      aria-label="Visualizador de PDF"
      className={cn(
        "flex min-h-0 flex-col overflow-hidden rounded-2xl bg-vacuo outline-offset-4",
        fullscreen ? "h-dvh rounded-none" : "h-[calc(100dvh-15rem)] min-h-[420px]",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-white/10 bg-estrato px-3 py-2 text-white">
        <div className="flex items-center gap-1">
          <IconButton label="Página anterior" onClick={() => go(page - 1)} disabled={page <= 1} className="text-white hover:bg-white/10 hover:text-white">
            <ChevronLeft className="size-5" aria-hidden />
          </IconButton>
          <label className="flex items-center gap-1.5 font-ui text-sm">
            <span className="sr-only">Página atual</span>
            <input
              type="number"
              min={1}
              max={total || 1}
              value={page}
              disabled={!total}
              onChange={(e) => e.target.value && go(Number(e.target.value))}
              className="h-8 w-14 rounded-md border border-white/20 bg-white/10 px-2 text-center tabular-nums text-white focus:border-ciano focus:outline-none"
            />
            <span aria-live="polite" className="text-white/80">
              / {total || "–"}
            </span>
          </label>
          <IconButton label="Próxima página" onClick={() => go(page + 1)} disabled={!total || page >= total} className="text-white hover:bg-white/10 hover:text-white">
            <ChevronRight className="size-5" aria-hidden />
          </IconButton>
        </div>
        <div className="flex items-center gap-1">
          <IconButton label="Diminuir zoom" onClick={() => setZoomIdx((z) => Math.max(z - 1, 0))} disabled={zoomIdx === 0} className="text-white hover:bg-white/10 hover:text-white">
            <ZoomOut className="size-5" aria-hidden />
          </IconButton>
          <span className="w-12 text-center font-ui text-sm tabular-nums" aria-live="polite">
            {Math.round(ZOOMS[zoomIdx] * 100)}%
          </span>
          <IconButton label="Aumentar zoom" onClick={() => setZoomIdx((z) => Math.min(z + 1, ZOOMS.length - 1))} disabled={zoomIdx === ZOOMS.length - 1} className="text-white hover:bg-white/10 hover:text-white">
            <ZoomIn className="size-5" aria-hidden />
          </IconButton>
          <IconButton label="Ajustar à largura" onClick={() => setZoomIdx(2)} className="text-white hover:bg-white/10 hover:text-white">
            <ScanLine className="size-5" aria-hidden />
          </IconButton>
          <IconButton label={fullscreen ? "Sair da tela cheia" : "Tela cheia"} onClick={toggleFullscreen} className="text-white hover:bg-white/10 hover:text-white">
            {fullscreen ? <Minimize className="size-5" aria-hidden /> : <Maximize className="size-5" aria-hidden />}
          </IconButton>
        </div>
      </div>

      <ProtectedSurface sessao={sessao} conteudoId={conteudoId} on="light" className="min-h-0 flex-1">
        <div ref={stageRef} className="absolute inset-0 overflow-auto">
          <div className="flex min-h-full w-max min-w-full justify-center p-4">
            <canvas
              ref={canvasRef}
              aria-label={`Página ${page} de ${total}`}
              className={cn(
                "h-fit rounded-sm bg-white shadow-pop transition-opacity",
                status !== "ready" && "hidden",
                rendering && "opacity-70",
              )}
            />
          </div>
        </div>
        {status === "loading" && (
          <div className="absolute inset-0 z-20 grid place-items-center bg-vacuo">
            <Spinner label="Carregando documento…" className="text-white/80" />
          </div>
        )}
        {status === "error" && (
          <div role="alert" className="absolute inset-0 z-20 grid place-items-center bg-vacuo p-6 text-center text-white">
            <div>
              <p className="text-lg font-bold">Não foi possível abrir o documento</p>
              <p className="mx-auto mt-2 max-w-md text-sm text-white/70">
                {errorMsg || "O link de acesso pode ter expirado."}
              </p>
              <Button className="mt-5" onClick={() => setAttempt((a) => a + 1)}>
                Tentar novamente
              </Button>
            </div>
          </div>
        )}
      </ProtectedSurface>
    </div>
  );
}
