import { Link } from "react-router-dom";
import { Eye, EyeOff, Pencil, Trash2 } from "lucide-react";
import { TypeTile, detalheMidia, tipoLabel } from "./MaterialCard";
import { Badge } from "@/components/ui/Badge";
import { Button, LinkButton } from "@/components/ui/Button";
import type { Conteudo } from "@/types";
import { formatDate } from "@/utils/format";

interface Props {
  c: Conteudo;
  busy?: boolean;
  onToggleStatus: (c: Conteudo) => void;
  onDelete: (c: Conteudo) => void;
}

/** Linha de gestão: situação de publicação e ações (editar, publicar/despublicar, excluir). */
export function ManageRow({ c, busy, onToggleStatus, onDelete }: Props) {
  const publicado = c.status === "PUBLICADO";
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded border border-line bg-white p-4 sm:flex-nowrap">
      <TypeTile tipo={c.tipo} />
      <div className="min-w-0 flex-1 basis-56">
        <div className="flex flex-wrap items-center gap-2">
          <Link to={`/material/${c.id}`} className="block truncate font-semibold hover:text-acao-700 hover:underline">
            {c.titulo}
          </Link>
          <Badge tone={publicado ? "ok" : "warn"}>{publicado ? "Publicado" : "Rascunho"}</Badge>
        </div>
        <p className="mt-0.5 truncate text-xs text-ink-soft">
          {c.turma.rotulo} · {tipoLabel(c.tipo)} · {detalheMidia(c)} · {formatDate(c.criadoEm)}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap gap-2">
        <LinkButton to={`/materiais/${c.id}/editar`} variant="secondary" size="sm" aria-label={`Editar ${c.titulo}`}>
          <Pencil className="size-4" aria-hidden /> Editar
        </LinkButton>
        <Button
          variant="secondary"
          size="sm"
          disabled={busy}
          onClick={() => onToggleStatus(c)}
          icon={publicado ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
          aria-label={`${publicado ? "Despublicar" : "Publicar"} ${c.titulo}`}
        >
          {publicado ? "Despublicar" : "Publicar"}
        </Button>
        <Button
          variant="danger"
          size="sm"
          disabled={busy}
          onClick={() => onDelete(c)}
          icon={<Trash2 className="size-4" aria-hidden />}
          aria-label={`Excluir ${c.titulo}`}
        >
          Excluir
        </Button>
      </div>
    </li>
  );
}
