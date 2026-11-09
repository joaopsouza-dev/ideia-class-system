import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { MailCheck } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { errorMessage, services } from "@/services";
import { validateEmail } from "@/utils/validation";
import { AuthLayout } from "./AuthLayout";

export default function ForgotPasswordPage() {
  useDocumentTitle("Recuperar senha");
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const err = validateEmail(email);
    setError(err);
    if (err) return;
    setLoading(true);
    setFormError(null);
    try {
      await services.auth.requestPasswordReset(email);
      setSent(true);
    } catch (e2) {
      setFormError(errorMessage(e2));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout title="Recuperar senha" subtitle="Informe seu e-mail para receber as instruções de redefinição.">
      {sent ? (
        <div role="status" className="text-center">
          <MailCheck className="mx-auto size-12 text-ok" aria-hidden />
          <p className="mt-3 font-semibold">Solicitação registrada</p>
          <p className="mt-1.5 text-sm text-ink-soft">
            Se existir uma conta para <strong>{email}</strong>, as instruções serão enviadas. (Demonstração: nenhum
            e-mail é realmente enviado.)
          </p>
          <Link to="/login" className="mt-6 inline-block text-sm font-semibold text-magenta-700 hover:underline">
            Voltar para o login
          </Link>
        </div>
      ) : (
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {formError && (
            <div role="alert" className="rounded-lg border border-danger/30 bg-danger-50 px-3.5 py-3 text-sm font-medium text-danger">
              {formError}
            </div>
          )}
          <TextField label="E-mail" type="email" autoComplete="email" value={email} error={error} onChange={(e) => setEmail(e.target.value)} placeholder="voce@exemplo.com" />
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            Enviar instruções
          </Button>
          <p className="text-center text-sm">
            <Link to="/login" className="font-semibold text-magenta-700 hover:underline">
              Voltar para o login
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  );
}
