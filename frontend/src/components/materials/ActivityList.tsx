import { AlertTriangle, Eye, FileUp, LogIn, LogOut, Pencil, Trash2 } from "lucide-react";
import type { LogAcesso, TipoAcao } from "@/types";
import { cn } from "@/utils/cn";
import { relativeTime } from "@/utils/format";

export const ACAO_LABEL: Record<TipoAcao, string> = {
  LOGIN: "Entrou na plataforma",
  LOGOUT: "Saiu da plataforma",
  ABRIU_PDF: "Abriu um PDF",
  ABRIU_VIDEO: "Assistiu a uma videoaula",
  TENTATIVA_CAPTURA: "Tentativa de captura de tela",
  TENTATIVA_IMPRESSAO: "Tentativa de impressão",
  TENTATIVA_DOWNLOAD: "Tentativa de download",
  TENTATIVA_COPIA: "Tentativa de cópia",
  PERDA_DE_FOCO: "Janela perdeu o foco",
  MARCA_DAGUA_ALTERADA: "Marca d'água alterada",
  UPLOAD: "Enviou um material",
  EDICAO: "Editou um material",
  PUBLICACAO: "Publicou um material",
  DESPUBLICACAO: "Despublicou um material",
  EXCLUSAO: "Excluiu um material",
};

export const isAlerta = (t: TipoAcao) =>
  t.startsWith("TENTATIVA_") || t === "MARCA_DAGUA_ALTERADA" || t === "PERDA_DE_FOCO";

export function acaoIcon(t: TipoAcao) {
  if (isAlerta(t)) return AlertTriangle;
  if (t === "LOGIN") return LogIn;
  if (t === "LOGOUT") return LogOut;
  if (t === "UPLOAD") return FileUp;
  if (t === "EDICAO") return Pencil;
  if (t === "EXCLUSAO") return Trash2;
  return Eye;
}

export function ActivityList({ items }: { items: LogAcesso[] }) {
  return (
    <ul className="divide-y divide-line">
      {items.map((l) => {
        const Icon = acaoIcon(l.tipoAcao);
        const alerta = isAlerta(l.tipoAcao);
        return (
          <li key={l.id} className="flex items-start gap-3 py-3">
            <span
              className={cn(
                "mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg",
                alerta ? "bg-warn-50 text-warn" : "bg-estelar text-estrato",
              )}
            >
              <Icon className="size-4" aria-hidden />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-sm">
                <strong>{l.usuarioNome}</strong> · {ACAO_LABEL[l.tipoAcao]}
              </p>
              {l.conteudoTitulo && <p className="truncate text-xs text-ink-soft">{l.conteudoTitulo}</p>}
            </div>
            <time dateTime={l.dataHora} className="shrink-0 text-xs text-ink-soft">
              {relativeTime(l.dataHora)}
            </time>
          </li>
        );
      })}
    </ul>
  );
}
