import { Link } from "react-router-dom";
import { ArrowRight, GraduationCap, Library, Users } from "lucide-react";
import { PageHeader } from "@/components/ui/Card";
import { CardGridSkeleton, EmptyState, ErrorState } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { services } from "@/services";

export default function ClassesPage() {
  useDocumentTitle("Turmas");
  const sessao = useSessao();
  const { data, loading, error, reload } = useAsync(() => services.academic.listTurmas(sessao), [sessao]);
  const perfil = sessao.usuario.perfil;

  return (
    <>
      <PageHeader
        eyebrow="Organização"
        title={perfil === "ADMIN" ? "Turmas" : "Minhas turmas"}
        description="Cada turma pertence a uma disciplina e reúne os materiais disponibilizados pelos docentes."
      />
      {loading && !data ? (
        <CardGridSkeleton count={4} />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : !data?.length ? (
        <EmptyState icon={GraduationCap} title="Você ainda não está em nenhuma turma" description="Procure a administração para ser vinculado a uma turma." />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {data.map((t) => (
            <Link
              key={t.id}
              to={`/turmas/${t.id}`}
              className="group rounded-2xl border border-line bg-white p-5 shadow-card transition-all hover:-translate-y-0.5 hover:border-magenta/50 hover:shadow-pop"
            >
              <p className="font-ui text-[11px] font-semibold uppercase tracking-[0.16em] text-ciano-700">
                {t.disciplinaCodigo} · {t.semestreAno}
              </p>
              <h2 className="mt-1 text-xl group-hover:text-magenta-700">{t.disciplinaNome}</h2>
              <p className="text-sm text-ink-soft">{t.nomeTurma}</p>
              <p className="mt-3 text-sm text-ink-soft">
                Docente{t.docentes.length > 1 ? "s" : ""}: {t.docentes.map((d) => d.nome).join(", ") || "—"}
              </p>
              <div className="mt-4 flex items-center gap-4 border-t border-line pt-4 text-sm">
                <span className="flex items-center gap-1.5 text-ink-soft">
                  <Library className="size-4" aria-hidden />
                  {t.totalMateriais} {t.totalMateriais === 1 ? "material" : "materiais"}
                </span>
                <span className="flex items-center gap-1.5 text-ink-soft">
                  <Users className="size-4" aria-hidden />
                  {t.totalAlunos} {t.totalAlunos === 1 ? "aluno" : "alunos"}
                </span>
                <ArrowRight className="ml-auto size-4 text-magenta transition-transform group-hover:translate-x-1" aria-hidden />
              </div>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
