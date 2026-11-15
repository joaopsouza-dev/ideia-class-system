import { useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import { Library, SearchX } from "lucide-react";
import { LibraryFilters } from "@/components/materials/LibraryFilters";
import { MaterialCard } from "@/components/materials/MaterialCard";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { Pagination } from "@/components/ui/Pagination";
import { CardGridSkeleton, EmptyState, ErrorState } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { useLibrary } from "@/hooks/useLibrary";
import { readProgress } from "@/hooks/useProgress";
import { services } from "@/services";

export default function LibraryPage() {
  useDocumentTitle("Biblioteca");
  const sessao = useSessao();
  const [params] = useSearchParams();
  const turmaParam = Number(params.get("turma")) || "";
  const { data, loading, error, reload } = useAsync(() => services.materials.list(sessao), [sessao]);
  const lib = useLibrary(data, { turmaId: turmaParam });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const progress = useMemo(() => readProgress(sessao.usuario.id), [sessao.usuario.id, data]);

  const grupos = useMemo(() => {
    const m = new Map<string, typeof lib.paged>();
    for (const c of lib.paged) m.set(c.turma.disciplinaNome, [...(m.get(c.turma.disciplinaNome) ?? []), c]);
    return Array.from(m.entries());
  }, [lib.paged]);

  return (
    <>
      <PageHeader
        eyebrow="Acervo"
        title="Biblioteca"
        description="Apostilas e videoaulas disponíveis para as suas turmas. Os conteúdos abrem no visualizador protegido."
      />
      {loading && !data ? (
        <CardGridSkeleton />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !data || data.length === 0 ? (
        <EmptyState
          icon={Library}
          title="Nenhum material por aqui ainda"
          description="Assim que houver conteúdos nas suas turmas, eles aparecerão nesta página."
        />
      ) : (
        <>
          <LibraryFilters
            filters={lib.filters}
            update={lib.update}
            reset={lib.reset}
            active={lib.active}
            items={data}
            total={lib.filtered.length}
          />
          {lib.filtered.length === 0 ? (
            <EmptyState
              icon={SearchX}
              title="Nenhum resultado"
              description="Nenhum material corresponde aos filtros aplicados."
              action={
                <Button variant="secondary" onClick={lib.reset}>
                  Limpar filtros
                </Button>
              }
            />
          ) : (
            <div className="space-y-8">
              {grupos.map(([nome, itens]) => (
                <section key={nome} aria-label={nome}>
                  <div className="flex flex-wrap items-baseline gap-3">
                    <h2 className="text-[22px] font-bold">{nome}</h2>
                    <span className="lbl text-ink-mute">
                      {itens[0].turma.rotulo} · {itens.length} {itens.length === 1 ? "material" : "materiais"}
                    </span>
                  </div>
                  <div className="mt-3">
                    {itens.map((c) => (
                      <MaterialCard key={c.id} c={c} progress={progress[c.id]} />
                    ))}
                  </div>
                </section>
              ))}
            </div>
          )}
          <Pagination page={lib.page} pageCount={lib.pageCount} total={lib.filtered.length} onChange={lib.setPage} />
        </>
      )}
    </>
  );
}
