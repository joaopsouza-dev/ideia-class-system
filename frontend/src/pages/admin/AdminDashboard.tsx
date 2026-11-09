import { Link } from "react-router-dom";
import { GraduationCap, Library, ShieldAlert, Users } from "lucide-react";
import { ActivityList, isAlerta } from "@/components/materials/ActivityList";
import { Card, PageHeader, StatCard } from "@/components/ui/Card";
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
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {loading ? (
          Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-28" />)
        ) : (
          <>
            <StatCard label="Usuários" value={u.length} hint={`${u.filter((x) => x.statusAtivo).length} ativos · ${u.filter((x) => !x.statusAtivo).length} inativos`} icon={Users} accent="magenta" />
            <StatCard label="Turmas" value={turmas.data!.length} hint="semestre 2026/2" icon={GraduationCap} accent="ciano" />
            <StatCard label="Materiais" value={mats.data!.length} hint={`${mats.data!.filter((c) => c.status === "PUBLICADO").length} publicados`} icon={Library} accent="nebulosa" />
            <StatCard label="Alertas" value={alertas} hint="tentativas bloqueadas (7 dias)" icon={ShieldAlert} accent="estrato" />
          </>
        )}
      </div>
      <Card className="mt-6">
        <div className="mb-1 flex items-center justify-between">
          <h2 className="text-lg">Eventos recentes</h2>
          <Link to="/auditoria" className="text-sm font-semibold text-magenta-700 hover:underline">
            Abrir auditoria
          </Link>
        </div>
        {logs.loading && !logs.data ? <Skeleton className="h-40" /> : <ActivityList items={(logs.data ?? []).slice(0, 8)} />}
      </Card>
    </>
  );
}
