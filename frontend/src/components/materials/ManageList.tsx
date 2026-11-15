import type { Conteudo } from "@/types";
import { ManageRow } from "./ManageRow";

/** Lista somente leitura (sem ações de gestão nesta etapa). */
export function ManageList({ items }: { items: Conteudo[] }) {
  return (
    <ul className="space-y-3">
      {items.map((c) => (
        <ManageRow key={c.id} c={c} />
      ))}
    </ul>
  );
}
