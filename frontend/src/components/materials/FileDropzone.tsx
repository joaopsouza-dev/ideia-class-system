import { useRef, useState, type DragEvent } from "react";
import { FileText, FileVideo, UploadCloud, X } from "lucide-react";
import { IconButton } from "@/components/ui/Button";
import type { TipoMaterial } from "@/types";
import { cn } from "@/utils/cn";
import { formatBytes } from "@/utils/format";

export const ACCEPT: Record<TipoMaterial, { mime: string[]; ext: string; label: string }> = {
  PDF: { mime: ["application/pdf"], ext: ".pdf", label: "PDF" },
  VIDEO: { mime: ["video/mp4", "video/webm"], ext: ".mp4,.webm", label: "MP4 ou WebM" },
};

export function validateFile(file: File, tipo: TipoMaterial, maxMb: number): string | null {
  const rule = ACCEPT[tipo];
  const okMime = rule.mime.includes(file.type) || (tipo === "PDF" && file.name.toLowerCase().endsWith(".pdf"));
  if (!okMime) return `Formato não aceito. Envie um arquivo ${rule.label}.`;
  if (file.size === 0) return "O arquivo está vazio.";
  if (file.size > maxMb * 1024 * 1024) return `O arquivo excede o limite de ${maxMb} MB.`;
  return null;
}

interface Props {
  tipo: TipoMaterial;
  file: File | null;
  onFile: (f: File | null) => void;
  maxMb: number;
  error?: string | null;
  disabled?: boolean;
  /** exibido quando não há arquivo novo mas já existe um (edição) */
  currentName?: string;
}

export function FileDropzone({ tipo, file, onFile, maxMb, error, disabled, currentName }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const rule = ACCEPT[tipo];
  const Icon = tipo === "PDF" ? FileText : FileVideo;
  const shownError = error ?? localError;

  function accept(f: File | undefined) {
    if (!f) return;
    const err = validateFile(f, tipo, maxMb);
    setLocalError(err);
    if (!err) onFile(f);
  }
  function onDrop(e: DragEvent) {
    e.preventDefault();
    setOver(false);
    if (!disabled) accept(e.dataTransfer.files[0]);
  }

  return (
    <div>
      <span className="font-ui text-[13px] font-medium">
        Arquivo <span className="text-acao-700" aria-hidden>*</span>
      </span>
      {file ? (
        <div className="mt-1.5 flex items-center gap-3 rounded border border-line bg-estelar p-3">
          <Icon className="size-8 shrink-0 text-estrato" aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{file.name}</p>
            <p className="text-xs text-ink-soft">{formatBytes(file.size)}</p>
          </div>
          <IconButton label="Remover arquivo" onClick={() => onFile(null)} disabled={disabled}>
            <X className="size-5" aria-hidden />
          </IconButton>
        </div>
      ) : (
        <button
          type="button"
          disabled={disabled}
          onClick={() => input.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setOver(true);
          }}
          onDragLeave={() => setOver(false)}
          onDrop={onDrop}
          className={cn(
            "mt-1.5 flex w-full flex-col items-center rounded border-2 border-dashed px-4 py-8 text-center transition-colors",
            over ? "border-acao bg-magenta-50" : "border-titanio/60 bg-white hover:border-estrato hover:bg-estelar",
            shownError && "border-danger",
            "disabled:cursor-not-allowed disabled:opacity-50",
          )}
        >
          <UploadCloud className="size-8 text-estrato" aria-hidden />
          <span className="mt-2 text-sm font-semibold">
            {currentName ? "Substituir arquivo (opcional)" : "Arraste o arquivo ou clique para escolher"}
          </span>
          <span className="mt-0.5 text-xs text-ink-soft">
            {rule.label} · até {maxMb} MB
            {currentName ? ` · atual: ${currentName}` : ""}
          </span>
        </button>
      )}
      <input
        ref={input}
        type="file"
        accept={rule.ext}
        className="sr-only"
        tabIndex={-1}
        aria-label="Selecionar arquivo"
        onChange={(e) => {
          accept(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
      {shownError && (
        <p role="alert" className="mt-1.5 text-[13px] font-medium text-danger">
          {shownError}
        </p>
      )}
    </div>
  );
}
