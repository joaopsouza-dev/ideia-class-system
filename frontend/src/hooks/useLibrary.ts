import { useMemo, useState } from "react";
import type { Conteudo, StatusPublicacao, TipoMaterial } from "@/types";
import { normalize } from "@/utils/format";
import { config } from "@/services/config";
import { useDebounced } from "./useDebounced";

export type SortKey = "recentes" | "antigos" | "titulo";

export interface LibraryFilters {
  busca: string;
  tipo: TipoMaterial | "";
  disciplinaId: number | "";
  turmaId: number | "";
  status: StatusPublicacao | "";
  sort: SortKey;
}

const INITIAL: LibraryFilters = { busca: "", tipo: "", disciplinaId: "", turmaId: "", status: "", sort: "recentes" };

/** Filtros, ordenação e paginação da biblioteca (client-side sobre o que a API devolveu). */
export function useLibrary(items: Conteudo[] | null, initial: Partial<LibraryFilters> = {}) {
  const [filters, setFilters] = useState<LibraryFilters>({ ...INITIAL, ...initial });
  const [page, setPage] = useState(1);
  const busca = useDebounced(filters.busca, 200);

  const update = <K extends keyof LibraryFilters>(k: K, v: LibraryFilters[K]) => {
    setFilters((f) => ({ ...f, [k]: v }));
    setPage(1);
  };
  const reset = () => {
    setFilters({ ...INITIAL, ...initial });
    setPage(1);
  };

  const filtered = useMemo(() => {
    const q = normalize(busca.trim());
    const list = (items ?? []).filter(
      (c) =>
        (!q || normalize(`${c.titulo} ${c.descricao} ${c.turma.rotulo} ${c.professor.nome}`).includes(q)) &&
        (!filters.tipo || c.tipo === filters.tipo) &&
        (!filters.status || c.status === filters.status) &&
        (!filters.disciplinaId || c.turma.disciplinaId === filters.disciplinaId) &&
        (!filters.turmaId || c.turma.id === filters.turmaId),
    );
    const sorted = [...list];
    if (filters.sort === "titulo") sorted.sort((a, b) => a.titulo.localeCompare(b.titulo, "pt-BR"));
    else
      sorted.sort((a, b) =>
        filters.sort === "recentes" ? b.criadoEm.localeCompare(a.criadoEm) : a.criadoEm.localeCompare(b.criadoEm),
      );
    return sorted;
  }, [items, busca, filters]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / config.pageSize));
  const safePage = Math.min(page, pageCount);
  const paged = filtered.slice((safePage - 1) * config.pageSize, safePage * config.pageSize);
  const active =
    !!filters.busca || !!filters.tipo || !!filters.status || !!filters.disciplinaId || !!filters.turmaId;

  return { filters, update, reset, active, filtered, paged, page: safePage, pageCount, setPage };
}
