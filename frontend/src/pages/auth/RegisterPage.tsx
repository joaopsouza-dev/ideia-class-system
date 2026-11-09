import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { Button, IconButton } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { useToast } from "@/context/ToastContext";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { errorMessage, services } from "@/services";
import { cn } from "@/utils/cn";
import { isValidCpf, maskCpf, passwordStrength, validateEmail, validatePassword } from "@/utils/validation";
import { AuthLayout } from "./AuthLayout";

type Errors = Partial<Record<"nome" | "email" | "cpf" | "senha" | "confirma" | "termos", string | null>>;

export default function RegisterPage() {
  useDocumentTitle("Cadastro");
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState({ nome: "", email: "", cpf: "", senha: "", confirma: "", termos: false });
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const strength = passwordStrength(form.senha);
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => {
    setForm((f) => ({ ...f, [k]: v }));
    const map: Partial<Record<keyof typeof form, keyof Errors>> = { nome: "nome", email: "email", cpf: "cpf", senha: "senha", confirma: "confirma", termos: "termos" };
    const key = map[k];
    if (key) setErrors((x) => ({ ...x, [key]: null }));
  };

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Errors = {
      nome: form.nome.trim().split(/\s+/).length < 2 ? "Informe nome e sobrenome." : null,
      email: validateEmail(form.email),
      cpf: isValidCpf(form.cpf) ? null : "CPF inválido.",
      senha: validatePassword(form.senha),
      confirma: form.confirma !== form.senha ? "As senhas não coincidem." : null,
      termos: form.termos ? null : "É necessário aceitar para continuar.",
    };
    setErrors(next);
    setFormError(null);
    if (Object.values(next).some(Boolean)) return;
    setLoading(true);
    try {
      await services.auth.register({
        nomeCompleto: form.nome,
        email: form.email,
        cpf: form.cpf.replace(/\D/g, ""),
        senha: form.senha,
      });
      toast.success("Cadastro concluído", "Agora você já pode entrar.");
      navigate("/login", { replace: true });
    } catch (err) {
      setFormError(errorMessage(err));
      setLoading(false);
    }
  }

  return (
    <AuthLayout title="Cadastre-se" subtitle="Crie sua conta para acessar os materiais da sua turma.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && (
          <div role="alert" className="rounded-lg border border-danger/30 bg-danger-50 px-3.5 py-3 text-sm font-medium text-danger">
            {formError}
          </div>
        )}
        <TextField label="Nome completo" autoComplete="name" value={form.nome} error={errors.nome} onChange={(e) => set("nome", e.target.value)} placeholder="Insira seu nome" />
        <TextField label="E-mail" type="email" autoComplete="email" value={form.email} error={errors.email} onChange={(e) => set("email", e.target.value)} placeholder="voce@exemplo.com" />
        <TextField
          label="CPF"
          inputMode="numeric"
          autoComplete="off"
          value={form.cpf}
          error={errors.cpf}
          onChange={(e) => set("cpf", maskCpf(e.target.value))}
          placeholder="000.000.000-00"
          hint="Usado só para identificação única. No backend deve ser armazenado criptografado."
        />
        <div>
          <TextField
            label="Senha"
            type={show ? "text" : "password"}
            autoComplete="new-password"
            value={form.senha}
            error={errors.senha}
            onChange={(e) => set("senha", e.target.value)}
            placeholder="Mínimo de 8 caracteres"
            trailing={
              <IconButton label={show ? "Ocultar senha" : "Mostrar senha"} onClick={() => setShow((s) => !s)}>
                {show ? <EyeOff className="size-[18px]" aria-hidden /> : <Eye className="size-[18px]" aria-hidden />}
              </IconButton>
            }
          />
          {form.senha && (
            <div className="mt-2" aria-live="polite">
              <div className="flex gap-1" aria-hidden>
                {[1, 2, 3, 4].map((i) => (
                  <span
                    key={i}
                    className={cn(
                      "h-1.5 flex-1 rounded-full",
                      i <= strength.score ? (strength.score <= 1 ? "bg-danger" : strength.score <= 2 ? "bg-warn" : "bg-ok") : "bg-line",
                    )}
                  />
                ))}
              </div>
              <p className="mt-1 text-xs text-ink-soft">Força da senha: {strength.label}</p>
            </div>
          )}
        </div>
        <TextField label="Confirmar senha" type={show ? "text" : "password"} autoComplete="new-password" value={form.confirma} error={errors.confirma} onChange={(e) => set("confirma", e.target.value)} />
        <div>
          <label className="flex items-start gap-2.5 text-sm text-ink-soft">
            <input
              type="checkbox"
              checked={form.termos}
              onChange={(e) => set("termos", e.target.checked)}
              className="mt-0.5 size-4 accent-magenta"
            />
            <span>
              Concordo que meu nome, e-mail e acessos serão registrados e exibidos como marca d'água nos conteúdos
              protegidos.
            </span>
          </label>
          {errors.termos && <p role="alert" className="mt-1 text-[13px] font-medium text-danger">{errors.termos}</p>}
        </div>
        <Button type="submit" size="lg" className="w-full" loading={loading}>
          {loading ? "Cadastrando…" : "Cadastrar"}
        </Button>
      </form>
      <p className="mt-5 text-center text-sm text-ink-soft">
        Já tem uma conta?{" "}
        <Link to="/login" className="font-semibold text-magenta-700 hover:underline">
          Faça seu login
        </Link>
      </p>
    </AuthLayout>
  );
}
