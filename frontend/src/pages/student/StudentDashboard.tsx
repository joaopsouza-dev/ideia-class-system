import { useMemo } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, BookOpen, GraduationCap, Sparkles } from "lucide-react";
import { MaterialCard, isNovo } from "@/components/materials/MaterialCard";
import { Card } from "@/components/ui/Card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { readProgress } from "@/hooks/useProgress";
import { services } from "@/services";

export default function StudentDashboard() {
  useDocumentTitle("Início");
  const sessao = useSessao();
  const mats = useAsync(() => services.materials.list(sessao), [sessao]);
  const turmas = useAsync(() => services.academic.listTurmas(sessao), [sessao]);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const progress = useMemo(() => readProgress(sessao.usuario.id), [sessao.usuario.id, mats.data]);

  const m = mats.data ?? [];
  const continuar = m
    .filter((c) => progress[c.id] && progress[c.id].posicao / (progress[c.id].total || 1) < 0.98)
    .sort((a, b) => progress[b.id].atualizadoEm.localeCompare(progress[a.id].atualizadoEm))
    .slice(0, 3);
  const novos = m.filter(isNovo).sort((a, b) => b.criadoEm.localeCompare(a.criadoEm)).slice(0, 3);
  const first = sessao.usuario.nomeCompleto.split(" ")[0];

  return (
    <>
      <section className="starfield relative mb-8 overflow-hidden rounded-2xl bg-vacuo p-6 text-white shadow-card sm:p-8">
        <div aria-hidden className="absolute -right-16 -top-16 size-64 rounded-full bg-magenta/25 blur-3xl" />
        <div aria-hidden className="absolute -bottom-20 right-24 size-56 rounded-full bg-nebulosa/30 blur-3xl" />
        <div className="relative">
          <p className="font-ui text-[11px] font-semibold uppercase tracking-[0.2em] text-ciano">Área do aluno</p>
          <h1 className="mt-1 text-2xl sm:text-3xl">Olá, {first}</h1>
          <p className="mt-2 max-w-xl text-sm text-white/75">
            Continue sua missão: seus materiais e videoaulas estão organizados por turma.
          </p>
          <Link
            to="/biblioteca"
            className="mt-5 inline-flex h-10 items-center gap-2 rounded-lg bg-magenta px-4 font-ui text-sm font-medium text-white transition-colors hover:bg-magenta-700"
          >
            <BookOpen className="size-4" aria-hidden /> Abrir biblioteca
          </Link>
        </div>
      </section>

      {mats.error && <ErrorState message={mats.error} onRetry={mats.reload} />}

      {continuar.length > 0 && (
        <section className="mb-8" aria-labelledby="continuar">
          <h2 id="continuar" className="mb-3 text-lg">Continuar de onde parou</h2>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {continuar.map((c) => (
              <MaterialCard key={c.id} c={c} progress={progress[c.id]} />
            ))}
          </div>
        </section>
      )}

      <section className="mb-8" aria-labelledby="novos">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="novos" className="flex items-center gap-2 text-lg">
            <Sparkles className="size-5 text-magenta" aria-hidden /> Novidades
          </h2>
          <Link to="/biblioteca" className="flex items-center gap-1 text-sm font-semibold text-magenta-700 hover:underline">
            Ver todos <ArrowRight className="size-4" aria-hidden />
          </Link>
        </div>
        {mats.loading && !mats.data ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {[0, 1, 2].map((i) => <Skeleton key={i} className="h-52" />)}
          </div>
        ) : novos.length ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {novos.map((c) => (
              <MaterialCard key={c.id} c={c} progress={progress[c.id]} />
            ))}
          </div>
        ) : (
          <EmptyState icon={Sparkles} title="Nada novo esta semana" description="Quando seus professores publicarem novos conteúdos, eles aparecem aqui." />
        )}
      </section>

      <section aria-labelledby="turmas">
        <h2 id="turmas" className="mb-3 text-lg">Minhas turmas</h2>
        {turmas.loading && !turmas.data ? (
          <Skeleton className="h-24" />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {turmas.data?.map((t) => (
              <Link key={t.id} to={`/turmas/${t.id}`}>
                <Card className="flex h-full items-center gap-3 p-4 transition-colors hover:border-magenta/50">
                  <span className="grid size-10 place-items-center rounded-xl bg-estrato/8 text-estrato">
                    <GraduationCap className="size-5" aria-hidden />
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-semibold">{t.disciplinaNome}</span>
                    <span className="block text-xs text-ink-soft">
                      {t.nomeTurma} · {t.totalMateriais} {t.totalMateriais === 1 ? "material" : "materiais"}
                    </span>
                  </span>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
