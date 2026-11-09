import { Link } from "react-router-dom";
import { Clock, FileText, Lock, PlayCircle, UserRound } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import type { ProgressEntry } from "@/hooks/useProgress";
import type { Conteudo } from "@/types";
import { cn } from "@/utils/cn";
import { daysSince, formatBytes, formatDate, formatDuration } from "@/utils/format";

export const isNovo = (c: Conteudo) => c.status === "PUBLICADO" && daysSince(c.criadoEm) <= 7;

export function TypeTile({ tipo, className }: { tipo: Conteudo["tipo"]; className?: string }) {
  const video = tipo === "VIDEO";
  const Icon = video ? PlayCircle : FileText;
  return (
    <span
      className={cn(
        "grid size-11 shrink-0 place-items-center rounded-xl",
        video ? "bg-nebulosa-50 text-nebulosa" : "bg-magenta-50 text-magenta",
        className,
      )}
      aria-hidden
    >
      <Icon className="size-6" />
    </span>
  );
}

export const tipoLabel = (t: Conteudo["tipo"]) => (t === "PDF" ? "PDF" : "Videoaula");

export function detalheMidia(c: Conteudo): string {
  if (c.tipo === "PDF") return `${c.paginas ? `${c.paginas} págs · ` : ""}${formatBytes(c.tamanhoBytes)}`;
  return c.duracaoSegundos ? formatDuration(c.duracaoSegundos) : formatBytes(c.tamanhoBytes);
}

export function MaterialCard({ c, progress }: { c: Conteudo; progress?: ProgressEntry }) {
  const pct = progress && progress.total ? Math.min(100, Math.round((progress.posicao / progress.total) * 100)) : 0;
  return (
    <Link
      to={`/material/${c.id}`}
      className="group flex flex-col rounded-2xl border border-line bg-white p-5 shadow-card transition-all hover:-translate-y-0.5 hover:border-magenta/50 hover:shadow-pop focus-visible:-translate-y-0.5"
    >
      <div className="flex items-start justify-between gap-3">
        <TypeTile tipo={c.tipo} />
        <div className="flex flex-wrap justify-end gap-1.5">
          {isNovo(c) && <Badge tone="magenta">Novo</Badge>}
          {c.status === "RASCUNHO" && <Badge tone="warn">Rascunho</Badge>}
          <Badge tone="dark" icon={<Lock className="size-3" aria-hidden />}>
            Protegido
          </Badge>
        </div>
      </div>
      <h3 className="mt-4 text-base leading-snug group-hover:text-magenta-700">{c.titulo}</h3>
      <p className="mt-1.5 line-clamp-2 text-sm text-ink-soft">{c.descricao}</p>
      <p className="mt-3 font-ui text-xs font-medium text-estrato">{c.turma.rotulo}</p>
      <dl className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-soft">
        <div className="flex items-center gap-1">
          <UserRound className="size-3.5" aria-hidden />
          <dt className="sr-only">Professor</dt>
          <dd>{c.professor.nome.split(" ").slice(0, 2).join(" ")}</dd>
        </div>
        <div className="flex items-center gap-1">
          <Clock className="size-3.5" aria-hidden />
          <dt className="sr-only">Publicação</dt>
          <dd>{formatDate(c.criadoEm)}</dd>
        </div>
        <div>
          <dt className="sr-only">Tipo</dt>
          <dd>
            {tipoLabel(c.tipo)} · {detalheMidia(c)}
          </dd>
        </div>
      </dl>
      {pct > 0 && (
        <div className="mt-4">
          <div className="h-1.5 overflow-hidden rounded-full bg-estelar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Progresso">
            <div className="h-full rounded-full bg-ciano" style={{ width: `${pct}%` }} />
          </div>
          <p className="mt-1 text-xs text-ink-soft">{pct >= 98 ? "Concluído" : `Continuar de ${pct}%`}</p>
        </div>
      )}
    </Link>
  );
}
