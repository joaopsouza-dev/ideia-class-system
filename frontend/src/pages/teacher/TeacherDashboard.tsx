import { Link } from "react-router-dom";
import { GraduationCap, Plus } from "lucide-react";
import { ActivityList, isAlerta } from "@/components/materials/ActivityList";
import { TypeTile, detalheMidia } from "@/components/materials/MaterialCard";
import { Badge } from "@/components/ui/Badge";
import { LinkButton } from "@/components/ui/Button";
import { Card, PageHeader, TelemetryStrip } from "@/components/ui/Card";
import { ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { services } from "@/services";
import { daysSince, formatDate } from "@/utils/format";

export default function TeacherDashboard() {
  useDocumentTitle("Painel do professor");
  const sessao = useSessao();
  const mats = useAsync(() => services.materials.list(sessao), [sessao]);
  const turmas = useAsync(() => services.academic.listTurmas(sessao), [sessao]);
  const logs = useAsync(() => services.accessLog.list(sessao), [sessao]);

  const m = mats.data ?? [];
  const publicados = m.filter((c) => c.status === "PUBLICADO").length;
  const rascunhos = m.length - publicados;
  const recentLogs = (logs.data ?? []).filter((l) => daysSince(l.dataHora) <= 7);
  const visualizacoes = recentLogs.filter((l) => l.tipoAcao === "ABRIU_PDF" || l.tipoAcao === "ABRIU_VIDEO").length;
  const alertas = recentLogs.filter((l) => isAlerta(l.tipoAcao)).length;
  const first = sessao.usuario.nomeCompleto.split(" ")[0];

  return (
    <>
      <PageHeader
        eyebrow="Painel do professor"
        title={`Olá, Prof. ${first}`}
        description="Resumo dos materiais das suas turmas e da atividade recente nos conteúdos."
        actions={
          <LinkButton to="/materiais/novo">
            <Plus className="size-4" aria-hidden /> Novo material
          </LinkButton>
        }
      />

      {mats.error ? (
        <ErrorState message={mats.error} onRetry={mats.reload} />
      ) : (
        mats.loading && !mats.data ? (
          <Skeleton className="h-32" />
        ) : (
          <TelemetryStrip
            label="Telemetria dos seus materiais"
            items={[
              { label: "Publicados", value: publicados, hint: "visíveis aos alunos" },
              { label: "Rascunhos", value: rascunhos, hint: "aguardando publicação", tone: "alerta" },
              { label: "Visualizações", value: visualizacoes, hint: "nos últimos 7 dias", tone: "ciano" },
              { label: "Alertas", value: alertas, hint: "tentativas bloqueadas (7 dias)" },
            ]}
          />
        )
      )}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <Card>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-[22px]">Materiais recentes</h2>
            <Link to="/biblioteca" className="text-sm font-semibold text-acao-700 hover:underline">
              Ver biblioteca
            </Link>
          </div>
          {mats.loading && !mats.data ? (
            <Skeleton className="h-40" />
          ) : m.length === 0 ? (
            <p className="py-6 text-center text-sm text-ink-soft">Nenhum material cadastrado ainda.</p>
          ) : (
            <ul className="divide-y divide-line">
              {[...m]
                .sort((a, b) => b.criadoEm.localeCompare(a.criadoEm))
                .slice(0, 5)
                .map((c) => (
                  <li key={c.id} className="flex items-center gap-3 py-3">
                    <TypeTile tipo={c.tipo} className="size-10" />
                    <div className="min-w-0 flex-1">
                      <Link to={`/material/${c.id}`} className="block truncate text-sm font-semibold hover:text-acao-700 hover:underline">
                        {c.titulo}
                      </Link>
                      <p className="truncate text-xs text-ink-soft">
                        {c.turma.rotulo} · {detalheMidia(c)} · {formatDate(c.criadoEm)}
                      </p>
                    </div>
                    <Badge tone={c.status === "PUBLICADO" ? "ok" : "warn"}>
                      {c.status === "PUBLICADO" ? "Publicado" : "Rascunho"}
                    </Badge>
                  </li>
                ))}
            </ul>
          )}
        </Card>

        <div className="space-y-6">
          <Card>
            <h2 className="mb-3 text-[22px]">Minhas turmas</h2>
            {turmas.loading && !turmas.data ? (
              <Skeleton className="h-24" />
            ) : (
              <ul className="space-y-2">
                {turmas.data?.map((t) => (
                  <li key={t.id}>
                    <Link
                      to={`/turmas/${t.id}`}
                      className="flex items-center gap-3 rounded border border-line p-3 transition-colors hover:border-acao/50 hover:bg-estelar"
                    >
                      <GraduationCap className="size-5 text-estrato" aria-hidden />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-semibold">{t.rotulo}</span>
                        <span className="block text-xs text-ink-soft">
                          {t.totalMateriais} {t.totalMateriais === 1 ? "material" : "materiais"} · {t.totalAlunos} {t.totalAlunos === 1 ? "aluno" : "alunos"}
                        </span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card>
            <div className="mb-1 flex items-center justify-between">
              <h2 className="text-[22px]">Atividade recente</h2>
              <Link to="/auditoria" className="text-sm font-semibold text-acao-700 hover:underline">
                Ver tudo
              </Link>
            </div>
            {logs.loading && !logs.data ? (
              <Skeleton className="h-32" />
            ) : logs.data?.length ? (
              <ActivityList items={logs.data.slice(0, 5)} />
            ) : (
              <p className="py-6 text-center text-sm text-ink-soft">Sem eventos registrados.</p>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
