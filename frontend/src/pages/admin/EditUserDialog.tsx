import { useEffect, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { SelectField, TextField } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/context/ToastContext";
import { PERFIL_LABEL } from "@/layouts/nav";
import { errorMessage, services } from "@/services";
import type { Perfil, Sessao, UsuarioAdmin } from "@/types";
import { validateEmail } from "@/utils/validation";

const FORM_ID = "form-editar-usuario";

interface EditUserDialogProps {
  sessao: Sessao;
  usuario: UsuarioAdmin | null;
  onClose: () => void;
  onSaved: () => void;
}

interface Erros {
  nome?: string;
  email?: string;
}

export function EditUserDialog({ sessao, usuario, onClose, onSaved }: EditUserDialogProps) {
  const toast = useToast();
  const [nome, setNome] = useState("");
  const [email, setEmail] = useState("");
  const [perfil, setPerfil] = useState<Perfil>("ALUNO");
  const [erros, setErros] = useState<Erros>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!usuario) return;
    setNome(usuario.nomeCompleto);
    setEmail(usuario.email);
    setPerfil(usuario.perfil);
    setErros({});
  }, [usuario]);

  const proprio = usuario?.id === sessao.usuario.id;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!usuario) return;
    const eNome = nome.trim().length < 3 ? "O nome deve ter ao menos 3 caracteres." : undefined;
    const eEmail = validateEmail(email) ?? undefined;
    setErros({ nome: eNome, email: eEmail });
    if (eNome || eEmail) return;

    const mudouPerfil = !proprio && perfil !== usuario.perfil;
    setSaving(true);
    try {
      await services.academic.atualizarUsuario(sessao, usuario.id, {
        nomeCompleto: nome.trim(),
        email: email.trim(),
        ...(mudouPerfil ? { perfil } : {}),
      });
      toast.success("Dados atualizados", nome.trim());
      onSaved();
    } catch (err) {
      toast.error("Não foi possível salvar", errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      open={!!usuario}
      title="Editar usuário"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button type="submit" form={FORM_ID} loading={saving}>
            Salvar
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={onSubmit} className="space-y-4" noValidate>
        <TextField
          label="Nome completo"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          error={erros.nome}
          autoComplete="off"
          data-autofocus
        />
        <TextField
          label="E-mail"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={erros.email}
          autoComplete="off"
        />
        <SelectField
          label="Perfil"
          value={perfil}
          onChange={(e) => setPerfil(e.target.value as Perfil)}
          disabled={proprio}
          hint={proprio ? "Você não pode alterar o próprio perfil." : undefined}
        >
          {(["ADMIN", "PROFESSOR", "ALUNO"] as Perfil[]).map((p) => (
            <option key={p} value={p}>
              {PERFIL_LABEL[p]}
            </option>
          ))}
        </SelectField>
      </form>
    </Modal>
  );
}
