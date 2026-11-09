import { Search, X } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { FilterSelect } from "@/components/ui/Field";
import type { LibraryFilters as Filters } from "@/hooks/useLibrary";
import type { Conteudo } from "@/types";

interface Props {
  filters: Filters;
  update: <K extends keyof Filters>(k: K, v: Filters[K]) => void;
  reset: () => void;
  active: boolean;
  items: Conteudo[];
  showStatus?: boolean;
  total: number;
}

export function LibraryFilters({ filters, update, reset, active, items, showStatus, total }: Props) {
  const disciplinas = Array.from(new Map(items.map((c) => [c.turma.disciplinaId, c.turma.disciplinaNome])));
  const turmas = Array.from(new Map(items.map((c) => [c.turma.id, c.turma.rotulo])));
  return (
    <div className="mb-5 rounded-2xl border border-line bg-white p-3 shadow-card">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-titanio" aria-hidden />
          <input
            type="search"
            value={filters.busca}
            onChange={(e) => update("busca", e.target.value)}
            placeholder="Buscar por título, descrição, turma ou professor"
            aria-label="Buscar materiais"
            className="h-10 w-full rounded-lg border border-line bg-white pl-9 pr-3 text-sm placeholder:text-titanio hover:border-estrato/40 focus:border-estrato focus:outline-none focus:ring-2 focus:ring-ciano/60"
          />
        </div>
        <FilterSelect label="Tipo" value={filters.tipo} onChange={(e) => update("tipo", e.target.value as Filters["tipo"])}>
          <option value="">Todos os tipos</option>
          <option value="PDF">PDF / Apostila</option>
          <option value="VIDEO">Videoaula</option>
        </FilterSelect>
        <FilterSelect
          label="Disciplina"
          value={filters.disciplinaId}
          onChange={(e) => update("disciplinaId", e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Todas as disciplinas</option>
          {disciplinas.map(([id, nome]) => (
            <option key={id} value={id}>
              {nome}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          label="Turma"
          value={filters.turmaId}
          onChange={(e) => update("turmaId", e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Todas as turmas</option>
          {turmas.map(([id, rot]) => (
            <option key={id} value={id}>
              {rot}
            </option>
          ))}
        </FilterSelect>
        {showStatus && (
          <FilterSelect label="Situação" value={filters.status} onChange={(e) => update("status", e.target.value as Filters["status"])}>
            <option value="">Todas as situações</option>
            <option value="PUBLICADO">Publicados</option>
            <option value="RASCUNHO">Rascunhos</option>
          </FilterSelect>
        )}
        <FilterSelect label="Ordenar por" value={filters.sort} onChange={(e) => update("sort", e.target.value as Filters["sort"])}>
          <option value="recentes">Mais recentes</option>
          <option value="antigos">Mais antigos</option>
          <option value="titulo">Título (A–Z)</option>
        </FilterSelect>
      </div>
      <div className="mt-2 flex items-center justify-between px-1 text-xs text-ink-soft" aria-live="polite">
        <span>
          {total} {total === 1 ? "material encontrado" : "materiais encontrados"}
        </span>
        {active && (
          <Button variant="ghost" size="sm" onClick={reset} icon={<X className="size-3.5" aria-hidden />}>
            Limpar filtros
          </Button>
        )}
      </div>
    </div>
  );
}
