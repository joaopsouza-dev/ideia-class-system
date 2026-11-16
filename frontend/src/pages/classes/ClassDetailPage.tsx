import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Library, UserMinus, UserPlus, Users } from "lucide-react";
import { MaterialCard } from "@/components/materials/MaterialCard";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { FilterSelect } from "@/components/ui/Field";
import { ConfirmDialog } from "@/components/ui/Modal";
import { CardGridSkeleton, EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { readProgress } from "@/hooks/useProgress";
import { errorMessage, services } from "@/services";
import type { ParticipanteTurma } from "@/types";

export default function ClassDetailPage() {
  const { turmaId } = useParams();
  const tid = Number(turmaId);
  const sessao = useSessao();
  const toast = useToast();
  const turma = useAsync(() => services.academic.getTurma(sessao, tid), [sessao, tid]);
  const mats = useAsync(() => services.materials.list(sessao, { turmaId: tid }), [sessao, tid]);
  useDocumentTitle(turma.data?.rotulo ?? "Turma");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const progress = useMemo(() => readProgress(sessao.usuario.id), [sessao.usuario.id, mats.data]);

  // Gestão de matrículas: administrador ou docente desta turma. O backend e o RLS reforçam a regra.
  const podeGerir =
    sessao.usuario.perfil === "ADMIN" ||
    (turma.data?.docentes.some((d) => d.id === sessao.usuario.id) ?? false);

  const participantes = useAsync(
    () => services.academic.listParticipantes(sessao, tid),
    [sessao, tid],
  );
  const disponiveis = useAsync(
    () => (podeGerir ? services.academic.listAlunosDisponiveis(sessao) : Promise.resolve([])),
    [sessao, podeGerir],
  );

  const [novoAlunoId, setNovoAlunoId] = useState<string>("");
  const [adicionando, setAdicionando] = useState(false);
  const [remover, setRemover] = useState<ParticipanteTurma | null>(null);
  const [removendo, setRemovendo] = useState(false);

  const alunosMatriculados = useMemo(
    () => (participantes.data ?? []).filter((p) => p.papel === "ALUNO" && p.status === "ATIVA"),
    [participantes.data],
  );
  const matriculadosIds = useMemo(
    () => new Set(alunosMatriculados.map((p) => p.usuarioId)),
    [alunosMatriculados],
  );
  const candidatos = useMemo(
    () => (disponiveis.data ?? []).filter((a) => !matriculadosIds.has(a.id)),
    [disponiveis.data, matriculadosIds],
  );

  function recarregarMatriculas() {
    participantes.reload();
    disponiveis.reload();
    turma.reload();
  }

  async function adicionarAluno() {
    const usuarioId = Number(novoAlunoId);
    if (!usuarioId) return;
    setAdicionando(true);
    try {
      const vinculo = await services.academic.vincularAluno(sessao, tid, usuarioId);
      toast.success("Aluno matriculado", vinculo.nome);
      setNovoAlunoId("");
      recarregarMatriculas();
    } catch (e) {
      toast.error("Não foi possível matricular o aluno", errorMessage(e));
    } finally {
      setAdicionando(false);
    }
  }

  async function confirmarRemocao() {
    if (!remover) return;
    setRemovendo(true);
    try {
      await services.academic.removerParticipante(sessao, tid, remover.vinculoId);
      toast.success("Aluno removido da turma", remover.nome);
      setRemover(null);
      recarregarMatriculas();
    } catch (e) {
      toast.error("Não foi possível remover o aluno", errorMessage(e));
    } finally {
      setRemovendo(false);
    }
  }

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
      />

      <section aria-labelledby="alunos-turma" className="mb-10">
        <h2 id="alunos-turma" className="mb-3 flex items-center gap-2 text-base font-semibold">
          <Users className="size-4" aria-hidden /> Alunos matriculados
        </h2>

        {podeGerir && (
          <div className="mb-4 flex flex-wrap items-end gap-2">
            <FilterSelect
              label="Aluno disponível"
              value={novoAlunoId}
              onChange={(e) => setNovoAlunoId(e.target.value)}
            >
              <option value="">{disponiveis.loading ? "Carregando…" : "Selecione um aluno"}</option>
              {candidatos.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.nomeCompleto} ({a.email})
                </option>
              ))}
            </FilterSelect>
            <Button
              variant="primary"
              size="sm"
              icon={<UserPlus className="size-4" aria-hidden />}
              disabled={!novoAlunoId || adicionando}
              onClick={adicionarAluno}
            >
              {adicionando ? "Matriculando…" : "Matricular"}
            </Button>
            {disponiveis.error && <p className="text-sm text-danger">{disponiveis.error}</p>}
          </div>
        )}

        {participantes.loading && !participantes.data ? (
          <div className="space-y-2">{[0, 1].map((i) => <Skeleton key={i} className="h-14" />)}</div>
        ) : participantes.error ? (
          <ErrorState message={participantes.error} onRetry={participantes.reload} />
        ) : alunosMatriculados.length === 0 ? (
          <EmptyState icon={Users} title="Nenhum aluno matriculado" description="Os alunos matriculados nesta turma aparecerão aqui." />
        ) : (
          <ul className="space-y-2">
            {alunosMatriculados.map((p) => (
              <li key={p.vinculoId} className="flex flex-wrap items-center gap-3 rounded border border-line bg-white p-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{p.nome}</p>
                  <p className="truncate text-xs text-ink-soft">{p.email}</p>
                </div>
                {podeGerir && (
                  <Button
                    variant="secondary"
                    size="sm"
                    icon={<UserMinus className="size-4" aria-hidden />}
                    onClick={() => setRemover(p)}
                  >
                    Remover
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {mats.loading && !mats.data ? (
        <CardGridSkeleton count={3} />
      ) : mats.error ? (
        <ErrorState message={mats.error} onRetry={mats.reload} />
      ) : !mats.data?.length ? (
        <EmptyState
          icon={Library}
          title="Sem materiais nesta turma"
          description="Ainda não há conteúdos nesta turma."
        />
      ) : (
        <div className="mt-3">
          {mats.data.map((c) => (
            <MaterialCard key={c.id} c={c} progress={progress[c.id]} />
          ))}
        </div>
      )}

      <ConfirmDialog
        open={!!remover}
        title="Remover aluno da turma?"
        confirmLabel="Remover"
        description={
          <>
            <strong className="text-ink">{remover?.nome}</strong> deixará de ter acesso aos materiais desta turma.
          </>
        }
        loading={removendo}
        onConfirm={confirmarRemocao}
        onCancel={() => setRemover(null)}
      />
    </>
  );
}
