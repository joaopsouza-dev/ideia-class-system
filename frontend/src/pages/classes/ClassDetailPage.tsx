import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Library, Plus } from "lucide-react";
import { ManageList } from "@/components/materials/ManageList";
import { MaterialCard } from "@/components/materials/MaterialCard";
import { LinkButton } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { CardGridSkeleton, EmptyState, ErrorState } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { readProgress } from "@/hooks/useProgress";
import { services } from "@/services";

export default function ClassDetailPage() {
  const { turmaId } = useParams();
  const tid = Number(turmaId);
  const sessao = useSessao();
  const manage = sessao.usuario.perfil !== "ALUNO";
  const turma = useAsync(() => services.academic.getTurma(sessao, tid), [sessao, tid]);
  const mats = useAsync(() => services.materials.list(sessao, { turmaId: tid }), [sessao, tid]);
  useDocumentTitle(turma.data?.rotulo ?? "Turma");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const progress = useMemo(() => readProgress(sessao.usuario.id), [sessao.usuario.id, mats.data]);

  if (turma.error) return <ErrorState message={turma.error} onRetry={turma.reload} />;
  const t = turma.data;

  return (
    <>
      <Link to="/turmas" className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden /> Turmas
      </Link>
      <PageHeader
        eyebrow={t ? `${t.disciplinaCodigo} · ${t.semestreAno}` : "Turma"}
        title={t?.rotulo ?? "Carregando…"}
        description={t ? `Docente${t.docentes.length > 1 ? "s" : ""}: ${t.docentes.map((d) => d.nome).join(", ")} · ${t.totalAlunos} alunos` : undefined}
        actions={
          manage && (
            <LinkButton to={`/materiais/novo?turma=${tid}`}>
              <Plus className="size-4" aria-hidden /> Novo material
            </LinkButton>
          )
        }
      />
      {mats.loading && !mats.data ? (
        <CardGridSkeleton count={3} />
      ) : mats.error ? (
        <ErrorState message={mats.error} onRetry={mats.reload} />
      ) : !mats.data?.length ? (
        <EmptyState
          icon={Library}
          title="Sem materiais nesta turma"
          description={manage ? "Cadastre a primeira apostila ou videoaula." : "Os professores ainda não publicaram conteúdos aqui."}
          action={manage && <LinkButton to={`/materiais/novo?turma=${tid}`}>Cadastrar material</LinkButton>}
        />
      ) : manage ? (
        <ManageList items={mats.data} onChanged={mats.reload} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {mats.data.map((c) => (
            <MaterialCard key={c.id} c={c} progress={progress[c.id]} />
          ))}
        </div>
      )}
    </>
  );
}
