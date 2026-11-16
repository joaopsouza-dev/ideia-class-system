import type { Conteudo } from "@/types";
import { ManageRow } from "./ManageRow";

interface Props {
  items: Conteudo[];
  busyId?: string | null;
  onToggleStatus: (c: Conteudo) => void;
  onDelete: (c: Conteudo) => void;
}

export function ManageList({ items, busyId, onToggleStatus, onDelete }: Props) {
  return (
    <ul className="space-y-3">
      {items.map((c) => (
        <ManageRow key={c.id} c={c} busy={busyId === c.id} onToggleStatus={onToggleStatus} onDelete={onDelete} />
      ))}
    </ul>
  );
}
