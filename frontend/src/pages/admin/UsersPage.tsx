import { useMemo, useState } from "react";
import { Search, UserCheck, UserX, Users } from "lucide-react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { FilterSelect } from "@/components/ui/Field";
import { ConfirmDialog } from "@/components/ui/Modal";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PERFIL_LABEL } from "@/layouts/nav";
import { errorMessage, services } from "@/services";
import type { Perfil, UsuarioAdmin } from "@/types";
import { formatDate, initials, normalize } from "@/utils/format";

export default function UsersPage() {
  useDocumentTitle("Usuários");
  const sessao = useSessao();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => services.academic.listUsuarios(sessao), [sessao]);
  const [busca, setBusca] = useState("");
  const [perfil, setPerfil] = useState<Perfil | "">("");
  const [status, setStatus] = useState<"" | "ativo" | "inativo">("");
  const [target, setTarget] = useState<UsuarioAdmin | null>(null);
  const [saving, setSaving] = useState(false);

  const list = useMemo(() => {
    const q = normalize(busca.trim());
    return (data ?? []).filter(
      (u) =>
        (!q || normalize(`${u.nomeCompleto} ${u.email}`).includes(q)) &&
        (!perfil || u.perfil === perfil) &&
        (!status || (status === "ativo") === u.statusAtivo),
    );
  }, [data, busca, perfil, status]);

  async function confirm() {
    if (!target) return;
    setSaving(true);
    try {
      await services.academic.setUsuarioAtivo(sessao, target.id, !target.statusAtivo);
      toast.success(target.statusAtivo ? "Usuário desativado" : "Usuário ativado", target.nomeCompleto);
      setTarget(null);
      reload();
    } catch (e) {
      toast.error("Não foi possível alterar o usuário", errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow="Administração" title="Usuários" description="Consulte perfis e vínculos com turmas e ative ou desative contas." />
      <div className="mb-6 flex flex-wrap items-center gap-2">
        <div className="relative min-w-56 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-mute" strokeWidth={1.75} aria-hidden />
          <input
            type="search"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            placeholder="Buscar por nome ou e-mail"
            aria-label="Buscar usuários"
            className="h-[34px] w-full rounded border border-line-strong bg-white pl-9 pr-3 text-[13px] placeholder:text-ink-mute hover:border-estrato/60 focus:border-estrato focus:outline-none focus:ring-2 focus:ring-ciano/60"
          />
        </div>
        <FilterSelect label="Perfil" value={perfil} onChange={(e) => setPerfil(e.target.value as Perfil | "")}>
          <option value="">Todos os perfis</option>
          <option value="ADMIN">Administrador</option>
          <option value="PROFESSOR">Professor</option>
          <option value="ALUNO">Aluno</option>
        </FilterSelect>
        <FilterSelect label="Situação" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}>
          <option value="">Todas as situações</option>
          <option value="ativo">Ativos</option>
          <option value="inativo">Inativos</option>
        </FilterSelect>
      </div>

      {loading && !data ? (
        <div className="space-y-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-20" />)}</div>
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : list.length === 0 ? (
        <EmptyState icon={Users} title="Nenhum usuário encontrado" description="Ajuste os filtros para ver outros resultados." />
      ) : (
        <ul className="space-y-3">
          {list.map((u) => (
            <li key={u.id} className="flex flex-wrap items-center gap-4 rounded border border-line bg-white p-4">
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-estrato font-ui text-sm font-semibold text-white">
                {initials(u.nomeCompleto)}
              </span>
              <div className="min-w-0 flex-1 basis-60">
                <p className="truncate font-semibold">{u.nomeCompleto}</p>
                <p className="truncate text-xs text-ink-soft">{u.email} · desde {formatDate(u.criadoEm)}</p>
                <div className="mt-1.5 flex flex-wrap gap-1.5">
                  {u.turmas.length === 0 ? (
                    <span className="text-xs text-ink-soft">Sem turmas</span>
                  ) : (
                    u.turmas.map((t) => (
                      <Badge key={`${t.id}-${t.papel}`} tone={t.papel === "DOCENTE" ? "nebulosa" : "ciano"}>
                        {t.rotulo}
                      </Badge>
                    ))
                  )}
                </div>
              </div>
              <Badge tone={u.perfil === "ADMIN" ? "dark" : u.perfil === "PROFESSOR" ? "magenta" : "neutral"}>
                {PERFIL_LABEL[u.perfil]}
              </Badge>
              <Badge tone={u.statusAtivo ? "ok" : "danger"}>{u.statusAtivo ? "Ativo" : "Inativo"}</Badge>
              <Button
                variant="secondary"
                size="sm"
                disabled={u.id === sessao.usuario.id}
                title={u.id === sessao.usuario.id ? "Você não pode desativar a própria conta" : undefined}
                icon={u.statusAtivo ? <UserX className="size-4" aria-hidden /> : <UserCheck className="size-4" aria-hidden />}
                onClick={() => setTarget(u)}
              >
                {u.statusAtivo ? "Desativar" : "Ativar"}
              </Button>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={!!target}
        title={target?.statusAtivo ? "Desativar usuário?" : "Ativar usuário?"}
        confirmLabel={target?.statusAtivo ? "Desativar" : "Ativar"}
        description={
          target?.statusAtivo ? (
            <>
              <strong className="text-ink">{target?.nomeCompleto}</strong> não conseguirá mais entrar na plataforma.
            </>
          ) : (
            <>
              <strong className="text-ink">{target?.nomeCompleto}</strong> poderá entrar novamente.
            </>
          )
        }
        loading={saving}
        onConfirm={confirm}
        onCancel={() => setTarget(null)}
      />
    </>
  );
}
