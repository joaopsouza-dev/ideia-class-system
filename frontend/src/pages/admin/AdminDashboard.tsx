import { Link } from "react-router-dom";
import { ActivityList, isAlerta } from "@/components/materials/ActivityList";
import { Card, PageHeader, TelemetryStrip } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { services } from "@/services";
import { daysSince } from "@/utils/format";

export default function AdminDashboard() {
  useDocumentTitle("Painel administrativo");
  const sessao = useSessao();
  const users = useAsync(() => services.academic.listUsuarios(sessao), [sessao]);
  const turmas = useAsync(() => services.academic.listTurmas(sessao), [sessao]);
  const mats = useAsync(() => services.materials.list(sessao), [sessao]);
  const logs = useAsync(() => services.accessLog.list(sessao), [sessao]);

  const u = users.data ?? [];
  const alertas = (logs.data ?? []).filter((l) => isAlerta(l.tipoAcao) && daysSince(l.dataHora) <= 7).length;
  const loading = !users.data || !turmas.data || !mats.data;

  return (
    <>
      <PageHeader eyebrow="Administração" title="Painel administrativo" description="Visão geral de usuários, turmas, conteúdos e eventos de segurança." />
      {loading ? (
        <Skeleton className="h-32" />
      ) : (
        <TelemetryStrip
          label="Telemetria da plataforma"
          items={[
            { label: "Usuários", value: u.length, hint: `${u.filter((x) => x.statusAtivo).length} ativos · ${u.filter((x) => !x.statusAtivo).length} inativos` },
            { label: "Turmas", value: turmas.data!.length, hint: "semestre 2026/2", tone: "ciano" },
            { label: "Materiais", value: mats.data!.length, hint: `${mats.data!.filter((c) => c.status === "PUBLICADO").length} publicados` },
            { label: "Alertas", value: alertas, hint: "tentativas bloqueadas (7 dias)", tone: "alerta" },
          ]}
        />
      )}
      <Card className="mt-6">
        <div className="mb-1 flex items-center justify-between">
          <h2 className="text-[22px]">Eventos recentes</h2>
          <Link to="/auditoria" className="text-sm font-semibold text-acao-700 hover:underline">
            Abrir auditoria
          </Link>
        </div>
        {logs.loading && !logs.data ? <Skeleton className="h-40" /> : <ActivityList items={(logs.data ?? []).slice(0, 8)} />}
      </Card>
    </>
  );
}
