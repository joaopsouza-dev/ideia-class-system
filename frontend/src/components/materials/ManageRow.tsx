import { Link } from "react-router-dom";
import { Eye, EyeOff, Pencil, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { Button, IconButton, buttonClass } from "@/components/ui/Button";
import type { Conteudo } from "@/types";
import { formatDate } from "@/utils/format";
import { TypeTile, detalheMidia, tipoLabel } from "./MaterialCard";

interface Props {
  c: Conteudo;
  busy?: boolean;
  onToggleStatus: (c: Conteudo) => void;
  onDelete: (c: Conteudo) => void;
}

/** Linha de gerenciamento (professor/admin): abrir, editar, publicar/despublicar, excluir. */
export function ManageRow({ c, busy, onToggleStatus, onDelete }: Props) {
  const publicado = c.status === "PUBLICADO";
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded border border-line bg-white p-4 sm:flex-nowrap">
      <TypeTile tipo={c.tipo} />
      <div className="min-w-0 flex-1 basis-56">
        <Link to={`/material/${c.id}`} className="block truncate font-semibold hover:text-acao-700 hover:underline">
          {c.titulo}
        </Link>
        <p className="mt-0.5 truncate text-xs text-ink-soft">
          {c.turma.rotulo} · {tipoLabel(c.tipo)} · {detalheMidia(c)} · {formatDate(c.criadoEm)}
        </p>
      </div>
      <Badge tone={publicado ? "ok" : "warn"}>{publicado ? "Publicado" : "Rascunho"}</Badge>
      <div className="ml-auto flex items-center gap-1">
        <Link to={`/material/${c.id}`} className={buttonClass("secondary", "sm")}>
          Abrir
        </Link>
        <Link to={`/materiais/${c.id}/editar`} className="inline-flex" aria-label={`Editar ${c.titulo}`} title="Editar">
          <span className="inline-flex size-9 items-center justify-center rounded text-ink-soft transition-colors hover:bg-estrato/8 hover:text-ink">
            <Pencil className="size-[18px]" aria-hidden />
          </span>
        </Link>
        <Button
          variant="ghost"
          size="sm"
          loading={busy}
          onClick={() => onToggleStatus(c)}
          icon={publicado ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
          aria-label={`${publicado ? "Despublicar" : "Publicar"} ${c.titulo}`}
        >
          {publicado ? "Despublicar" : "Publicar"}
        </Button>
        <IconButton label={`Excluir ${c.titulo}`} onClick={() => onDelete(c)} disabled={busy} className="hover:bg-danger-50 hover:text-danger">
          <Trash2 className="size-[18px]" aria-hidden />
        </IconButton>
      </div>
    </li>
  );
}
