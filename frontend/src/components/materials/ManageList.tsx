import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/Modal";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { errorMessage, services } from "@/services";
import type { Conteudo } from "@/types";
import { ManageRow } from "./ManageRow";

/** Lista de gerenciamento com publicar/despublicar e exclusão com confirmação. */
export function ManageList({ items, onChanged }: { items: Conteudo[]; onChanged: () => void }) {
  const sessao = useSessao();
  const toast = useToast();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<Conteudo | null>(null);
  const [deleting, setDeleting] = useState(false);

  async function toggle(c: Conteudo) {
    setBusyId(c.id);
    const next = c.status === "PUBLICADO" ? "RASCUNHO" : "PUBLICADO";
    try {
      await services.materials.setStatus(sessao, c.id, next);
      toast.success(next === "PUBLICADO" ? "Material publicado" : "Material despublicado", c.titulo);
      onChanged();
    } catch (e) {
      toast.error("Não foi possível alterar a publicação", errorMessage(e));
    } finally {
      setBusyId(null);
    }
  }

  async function confirmDelete() {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await services.materials.remove(sessao, toDelete.id);
      toast.success("Material excluído", toDelete.titulo);
      setToDelete(null);
      onChanged();
    } catch (e) {
      toast.error("Não foi possível excluir", errorMessage(e));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <>
      <ul className="space-y-3">
        {items.map((c) => (
          <ManageRow key={c.id} c={c} busy={busyId === c.id} onToggleStatus={toggle} onDelete={setToDelete} />
        ))}
      </ul>
      <ConfirmDialog
        open={!!toDelete}
        title="Excluir material?"
        description={
          <>
            <strong className="text-ink">{toDelete?.titulo}</strong> deixará de aparecer para os alunos. Os registros de
            acesso anteriores são preservados para auditoria.
          </>
        }
        loading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </>
  );
}
