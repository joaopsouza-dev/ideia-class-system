import { useMemo, useState } from "react";
import { Download, FileClock, Search } from "lucide-react";
import { ACAO_LABEL, isAlerta } from "@/components/materials/ActivityList";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { FilterSelect } from "@/components/ui/Field";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { services } from "@/services";
import type { LogAcesso } from "@/types";
import { formatDateTime, normalize } from "@/utils/format";

const PAGE = 12;
type Grupo = "" | "alertas" | "acessos" | "gestao";
const GESTAO = ["UPLOAD", "EDICAO", "PUBLICACAO", "DESPUBLICACAO", "EXCLUSAO"];

function toCsv(rows: LogAcesso[]): string {
  const esc = (v: string | number | null | undefined) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const head = ["data_hora", "usuario", "acao", "conteudo", "ip_origem", "dispositivo", "detalhe"];
  const body = rows.map((l) =>
    [l.dataHora, l.usuarioNome, l.tipoAcao, l.conteudoTitulo, l.ipOrigem, l.dispositivo, l.detalhe].map(esc).join(","),
  );
  return "﻿" + [head.join(","), ...body].join("\n");
}

export default function AuditPage() {
  const sessao = useSessao();
  const admin = sessao.usuario.perfil === "ADMIN";
  useDocumentTitle(admin ? "Auditoria" : "Acessos aos meus conteúdos");
  const { data, loading, error, reload } = useAsync(() => services.accessLog.list(sessao), [sessao]);
  const [busca, setBusca] = useState("");
  const [grupo, setGrupo] = useState<Grupo>("");
  const [page, setPage] = useState(1);

  const rows = useMemo(() => {
    const q = normalize(busca.trim());
    return (data ?? []).filter((l) => {
      const g =
        !grupo ||
        (grupo === "alertas" && isAlerta(l.tipoAcao)) ||
        (grupo === "gestao" && GESTAO.includes(l.tipoAcao)) ||
        (grupo === "acessos" && !isAlerta(l.tipoAcao) && !GESTAO.includes(l.tipoAcao));
      return g && (!q || normalize(`${l.usuarioNome} ${l.conteudoTitulo ?? ""} ${ACAO_LABEL[l.tipoAcao]}`).includes(q));
    });
  }, [data, busca, grupo]);

  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE));
  const current = Math.min(page, pageCount);
  const paged = rows.slice((current - 1) * PAGE, current * PAGE);

  function exportCsv() {
    const url = URL.createObjectURL(new Blob([toCsv(rows)], { type: "text/csv;charset=utf-8" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = `auditoria-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <PageHeader
        eyebrow={admin ? "Rastreabilidade" : "Seus conteúdos"}
        title={admin ? "Auditoria de acessos" : "Acessos aos meus conteúdos"}
        description="Registro de acessos e de tentativas de uso não permitido. Em produção, os registros são imutáveis no banco (log_acesso)."
        actions={
          <Button variant="secondary" onClick={exportCsv} disabled={!rows.length} icon={<Download className="size-4" aria-hidden />}>
            Exportar CSV
          </Button>
        }
      />
      <div className="mb-6 flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-mute" strokeWidth={1.75} aria-hidden />
          <input
            type="search"
            value={busca}
            onChange={(e) => {
              setBusca(e.target.value);
              setPage(1);
            }}
            placeholder="Buscar por usuário, conteúdo ou ação"
            aria-label="Buscar eventos"
            className="h-[34px] w-full rounded border border-line-strong bg-white pl-9 pr-3 text-[13px] placeholder:text-ink-mute hover:border-estrato/60 focus:border-estrato focus:outline-none focus:ring-2 focus:ring-ciano/60"
          />
        </div>
        <FilterSelect
          label="Tipo de evento"
          value={grupo}
          onChange={(e) => {
            setGrupo(e.target.value as Grupo);
            setPage(1);
          }}
        >
          <option value="">Todos os eventos</option>
          <option value="acessos">Acessos e visualizações</option>
          <option value="alertas">Alertas de proteção</option>
          <option value="gestao">Gestão de conteúdo</option>
        </FilterSelect>
      </div>

      {loading && !data ? (
        <Skeleton className="h-72" />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : rows.length === 0 ? (
        <EmptyState icon={FileClock} title="Nenhum evento encontrado" description="Não há registros para os filtros selecionados." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-line bg-white">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="bg-estelar font-ui text-[11px] uppercase tracking-wider text-ink-soft">
                <tr>
                  <th scope="col" className="px-4 py-3">Data/hora</th>
                  <th scope="col" className="px-4 py-3">Usuário</th>
                  <th scope="col" className="px-4 py-3">Ação</th>
                  <th scope="col" className="px-4 py-3">Conteúdo</th>
                  <th scope="col" className="px-4 py-3">Origem</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {paged.map((l) => (
                  <tr key={l.id} className="align-top hover:bg-estelar/60">
                    <td className="whitespace-nowrap px-4 py-3 tabular-nums text-ink-soft">{formatDateTime(l.dataHora)}</td>
                    <td className="px-4 py-3 font-medium">{l.usuarioNome}</td>
                    <td className="px-4 py-3">
                      <Badge tone={isAlerta(l.tipoAcao) ? "warn" : GESTAO.includes(l.tipoAcao) ? "nebulosa" : "ciano"}>
                        {ACAO_LABEL[l.tipoAcao]}
                      </Badge>
                      {l.detalhe && <p className="mt-1 text-xs text-ink-soft">{l.detalhe}</p>}
                    </td>
                    <td className="px-4 py-3 text-ink-soft">{l.conteudoTitulo ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-ink-soft">
                      {l.ipOrigem}
                      <br />
                      {l.dispositivo}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={current} pageCount={pageCount} total={rows.length} onChange={setPage} />
        </>
      )}
    </>
  );
}
