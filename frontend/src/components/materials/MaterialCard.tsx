import { Link } from "react-router-dom";
import { FileText, Lock, PlayCircle, UserRound } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import type { ProgressEntry } from "@/hooks/useProgress";
import type { Conteudo } from "@/types";
import { cn } from "@/utils/cn";
import { daysSince, formatBytes, formatDate, formatDuration } from "@/utils/format";

export const isNovo = (c: Conteudo) => c.status === "PUBLICADO" && daysSince(c.criadoEm) <= 7;

const SPINES = ["#E80074", "#00CFE5", "#7B2CBF"] as const;
const codigoDisciplina = (nome: string) =>
  nome.normalize("NFD").replace(/[^A-Za-z]/g, "").slice(0, 4).toUpperCase() || "IDEIA";

/** Capa procedural (sem imagens externas): lombada colorida por disciplina, grade técnica e órbita. */
export function MaterialCover({ c, className }: { c: Conteudo; className?: string }) {
  const video = c.tipo === "VIDEO";
  const spine = SPINES[c.turma.disciplinaId % SPINES.length];
  return (
    <svg viewBox="0 0 64 84" className={cn("h-[84px] w-16 shrink-0 rounded-[3px]", className)} aria-hidden>
      <rect width="64" height="84" fill={video ? "#1D1856" : "#0C0931"} />
      <rect width="7" height="84" fill={spine} />
      <g stroke="#8E9EAB" strokeOpacity=".25">
        <path d="M7 21H64M7 42H64M7 63H64M28 0V84M49 0V84" />
      </g>
      {video ? (
        <>
          <circle cx="38" cy="50" r="15" fill="none" stroke="#B8C0DC" />
          <path d="M34 43l12 7-12 7z" fill="#fff" />
        </>
      ) : (
        <>
          <ellipse cx="38" cy="50" rx="20" ry="9" transform="rotate(-18 38 50)" fill="none" stroke="#8E9EAB" strokeOpacity=".8" />
          <circle cx="32" cy="52" r="4" fill={spine} />
        </>
      )}
      <text x="14" y="16" fill="#F2F2F7" fontFamily="Poppins, sans-serif" fontSize="8" fontWeight="600" letterSpacing="1">
        {codigoDisciplina(c.turma.disciplinaNome)}
      </text>
    </svg>
  );
}

/** Ícone de tipo compacto para listas densas (gestão). */
export function TypeTile({ tipo, className }: { tipo: Conteudo["tipo"]; className?: string }) {
  const video = tipo === "VIDEO";
  const Icon = video ? PlayCircle : FileText;
  return (
    <span
      className={cn(
        "grid size-11 shrink-0 place-items-center rounded",
        video ? "bg-estrato text-white" : "bg-vacuo text-white",
        className,
      )}
      aria-hidden
    >
      <Icon className="size-5" strokeWidth={1.75} />
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
      className="group grid grid-cols-[4rem_minmax(0,1fr)] items-center gap-x-5 gap-y-3 border-t border-line py-4 sm:grid-cols-[4rem_minmax(0,1fr)_11rem_8.5rem_6rem]"
    >
      <MaterialCover c={c} />
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-[17px] font-semibold leading-snug group-hover:text-acao-700">{c.titulo}</h3>
          {isNovo(c) && <Badge tone="magenta">Novo</Badge>}
          {c.status === "RASCUNHO" && <Badge tone="warn">Rascunho</Badge>}
        </div>
        <p className="mt-1 line-clamp-1 text-sm text-ink-soft">{c.descricao}</p>
        <p className="mt-1 text-[12.5px] text-ink-mute">
          <UserRound className="mr-1 inline size-3.5 -translate-y-px" strokeWidth={1.75} aria-hidden />
          <span className="sr-only">Professor: </span>
          {c.professor.nome}
          <span aria-hidden> · </span>
          <span className="font-semibold text-estrato">{c.turma.rotulo}</span>
        </p>
        {pct > 0 && (
          <div className="mt-2.5 flex items-center gap-3">
            <div className="h-[3px] w-40 overflow-hidden rounded-full bg-line" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Progresso">
              <div className="h-full bg-acao" style={{ width: `${pct}%` }} />
            </div>
            <span className="text-xs text-ink-soft">{pct >= 98 ? "Concluído" : `Continuar de ${pct}%`}</span>
          </div>
        )}
      </div>
      <dl className="col-span-2 flex gap-x-8 sm:contents">
        <div>
          <dt className="lbl text-[10px] text-ink-mute">Formato</dt>
          <dd className="mt-0.5 whitespace-nowrap text-[13.5px] font-semibold">
            {tipoLabel(c.tipo)} · {detalheMidia(c)}
          </dd>
        </div>
        <div>
          <dt className="lbl text-[10px] text-ink-mute">Publicado</dt>
          <dd className="mt-0.5 whitespace-nowrap text-[13.5px] font-semibold tabular-nums">{formatDate(c.criadoEm)}</dd>
        </div>
      </dl>
      <span className="col-span-2 flex items-center justify-end gap-1.5 font-ui text-xs font-semibold uppercase tracking-[0.1em] text-estrato sm:col-span-1">
        <Lock className="size-3.5 text-ciano-700" strokeWidth={1.75} aria-hidden />
        Abrir →
      </span>
    </Link>
  );
}
