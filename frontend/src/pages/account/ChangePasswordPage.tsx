import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Card";
import { TextField } from "@/components/ui/Field";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { errorMessage, services } from "@/services";
import { validatePassword } from "@/utils/validation";

interface Erros {
  atual?: string;
  nova?: string;
  confirmacao?: string;
}

export default function ChangePasswordPage() {
  useDocumentTitle("Alterar senha");
  const sessao = useSessao();
  const toast = useToast();
  const [senhaAtual, setSenhaAtual] = useState("");
  const [novaSenha, setNovaSenha] = useState("");
  const [confirmacao, setConfirmacao] = useState("");
  const [erros, setErros] = useState<Erros>({});
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const novosErros: Erros = {
      atual: senhaAtual ? undefined : "Informe a senha atual.",
      nova: validatePassword(novaSenha) ?? (novaSenha === senhaAtual ? "A nova senha deve ser diferente da atual." : undefined) ?? undefined,
      confirmacao: confirmacao !== novaSenha ? "As senhas não coincidem." : undefined,
    };
    setErros(novosErros);
    if (novosErros.atual || novosErros.nova || novosErros.confirmacao) return;

    setSaving(true);
    try {
      await services.auth.alterarSenha(sessao, senhaAtual, novaSenha);
      toast.success("Senha alterada", "Use a nova senha nos próximos acessos.");
      setSenhaAtual("");
      setNovaSenha("");
      setConfirmacao("");
    } catch (err) {
      toast.error("Não foi possível alterar a senha", errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow="Conta" title="Alterar senha" description="Informe a senha atual e defina uma nova." />
      <form onSubmit={onSubmit} className="max-w-md space-y-5" noValidate>
        <TextField
          label="Senha atual"
          type="password"
          autoComplete="current-password"
          value={senhaAtual}
          onChange={(e) => setSenhaAtual(e.target.value)}
          error={erros.atual}
        />
        <TextField
          label="Nova senha"
          type="password"
          autoComplete="new-password"
          value={novaSenha}
          onChange={(e) => setNovaSenha(e.target.value)}
          error={erros.nova}
          hint="Mínimo de 8 caracteres."
        />
        <TextField
          label="Confirmar nova senha"
          type="password"
          autoComplete="new-password"
          value={confirmacao}
          onChange={(e) => setConfirmacao(e.target.value)}
          error={erros.confirmacao}
        />
        <Button type="submit" loading={saving}>
          Salvar nova senha
        </Button>
      </form>
    </>
  );
}
