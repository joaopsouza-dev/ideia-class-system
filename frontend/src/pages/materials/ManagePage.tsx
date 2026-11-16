import { useState } from "react";
import { FolderOpen, Plus } from "lucide-react";
import { ManageList } from "@/components/materials/ManageList";
import { LinkButton } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/Modal";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { errorMessage, services } from "@/services";
import type { Conteudo } from "@/types";

/** Gestão de materiais: o professor vê os próprios; o administrador vê todos. */
export default function ManagePage() {
  useDocumentTitle("Gerenciar materiais");
  const sessao = useSessao();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => services.materials.list(sessao), [sessao]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [target, setTarget] = useState<Conteudo | null>(null);
  const [deleting, setDeleting] = useState(false);

  const admin = sessao.usuario.perfil === "ADMIN";
  const items = (data ?? []).filter((c) => admin || c.professor.id === sessao.usuario.id);

  async function toggle(c: Conteudo) {
    const next = c.status === "PUBLICADO" ? "RASCUNHO" : "PUBLICADO";
    setBusyId(c.id);
    try {
      await services.materials.setStatus(sessao, c.id, next);
      toast.success(next === "PUBLICADO" ? "Material publicado" : "Material despublicado", c.titulo);
      reload();
    } catch (err) {
      toast.error("Não foi possível alterar a situação", errorMessage(err));
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDelete() {
    if (!target) return;
    setDeleting(true);
    try {
      await services.materials.remove(sessao, target.id);
      toast.success("Material excluído", target.titulo);
      setTarget(null);
      reload();
    } catch (err) {
      toast.error("Não foi possível excluir", errorMessage(err));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="Gestão"
        title="Gerenciar materiais"
        description="Cadastre, edite, publique e exclua os materiais. Rascunhos ficam visíveis apenas para você e administradores."
        actions={
          <LinkButton to="/materiais/novo">
            <Plus className="size-4" aria-hidden /> Novo material
          </LinkButton>
        }
      />
      {loading && !data ? (
        <Skeleton className="h-32" />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : items.length === 0 ? (
        <EmptyState
          icon={FolderOpen}
          title="Nenhum material cadastrado"
          description="Use “Novo material” para enviar um PDF ou uma videoaula."
        />
      ) : (
        <ManageList items={items} busyId={busyId} onToggleStatus={toggle} onDelete={setTarget} />
      )}

      <ConfirmDialog
        open={!!target}
        title="Excluir material?"
        confirmLabel="Excluir"
        description={
          <>
            <strong className="text-ink">{target?.titulo}</strong> deixará de aparecer para todos. Esta ação não pode ser
            desfeita pela interface.
          </>
        }
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setTarget(null)}
      />
    </>
  );
}
