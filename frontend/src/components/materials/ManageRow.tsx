import { Link } from "react-router-dom";
import { TypeTile, detalheMidia, tipoLabel } from "./MaterialCard";
import type { Conteudo } from "@/types";
import { formatDate } from "@/utils/format";

/** Simplified row for read‑only view (no status, no actions). */
export function ManageRow({ c }: { c: Conteudo }) {
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
    </li>
  );
}
