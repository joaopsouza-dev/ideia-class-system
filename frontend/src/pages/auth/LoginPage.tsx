import { useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Eye, EyeOff, GraduationCap, ShieldCheck, UserCog } from "lucide-react";
import { Button, IconButton } from "@/components/ui/Button";
import { TextField } from "@/components/ui/Field";
import { useAuth } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { errorMessage, isDemoMode } from "@/services";
import { DEMO_PASSWORD } from "@/mocks/seed";
import { validateEmail, validatePassword } from "@/utils/validation";
import { AuthLayout } from "./AuthLayout";

const DEMO_ACCOUNTS = [
  { label: "Professor", email: "victor.emanoel@ideiaspace.dev", icon: GraduationCap },
  { label: "Aluno", email: "maria.clara@ideiaspace.dev", icon: ShieldCheck },
  { label: "Admin", email: "admin@ideiaspace.dev", icon: UserCog },
];

export default function LoginPage() {
  useDocumentTitle("Entrar");
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState<{ email?: string | null; senha?: string | null }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const submitRef = useRef<HTMLButtonElement>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next = { email: validateEmail(email), senha: validatePassword(senha) };
    setErrors(next);
    setFormError(null);
    if (next.email || next.senha) return;
    setLoading(true);
    try {
      const s = await login(email, senha);
      toast.success(`Olá, ${s.usuario.nomeCompleto.split(" ")[0]}!`, "Login realizado.");
      navigate((location.state as { from?: string } | null)?.from ?? "/", { replace: true });
    } catch (err) {
      setFormError(errorMessage(err));
      setLoading(false);
    }
  }

  return (
    <AuthLayout title="Entrar" subtitle="Acesse o repositório digital protegido de conteúdo acadêmico.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {formError && (
          <div role="alert" className="rounded-lg border border-danger/30 bg-danger-50 px-3.5 py-3 text-sm font-medium text-danger">
            {formError}
          </div>
        )}
        <TextField
          label="E-mail"
          type="email"
          autoComplete="email"
          placeholder="voce@exemplo.com"
          value={email}
          error={errors.email}
          onChange={(e) => {
            setEmail(e.target.value);
            setErrors((x) => ({ ...x, email: null }));
          }}
        />
        <TextField
          label="Senha"
          type={show ? "text" : "password"}
          autoComplete="current-password"
          placeholder="Insira sua senha"
          value={senha}
          error={errors.senha}
          onChange={(e) => {
            setSenha(e.target.value);
            setErrors((x) => ({ ...x, senha: null }));
          }}
          trailing={
            <IconButton label={show ? "Ocultar senha" : "Mostrar senha"} onClick={() => setShow((s) => !s)}>
              {show ? <EyeOff className="size-[18px]" aria-hidden /> : <Eye className="size-[18px]" aria-hidden />}
            </IconButton>
          }
        />
        <div className="flex justify-end">
          <Link to="/recuperar-senha" className="text-sm font-medium text-magenta-700 hover:underline">
            Esqueci minha senha
          </Link>
        </div>
        <Button ref={submitRef} type="submit" size="lg" className="w-full" loading={loading}>
          {loading ? "Entrando…" : "Entrar"}
        </Button>
      </form>

      <p className="mt-5 text-center text-sm text-ink-soft">
        Não tem uma conta?{" "}
        <Link to="/cadastro" className="font-semibold text-magenta-700 hover:underline">
          Cadastre-se
        </Link>
      </p>

      {isDemoMode && (
        <div className="mt-6 rounded-xl border border-ciano/50 bg-ciano-50 p-4">
          <p className="font-ui text-xs font-semibold uppercase tracking-wider text-ciano-700">
            Contas de demonstração
          </p>
          <p className="mt-1 text-xs text-ink-soft">
            Preenche o formulário com dados fictícios do seed (senha <code className="font-semibold">{DEMO_PASSWORD}</code>).
          </p>
          <div className="mt-3 grid grid-cols-3 gap-2">
            {DEMO_ACCOUNTS.map(({ label, email: em, icon: Icon }) => (
              <button
                key={em}
                type="button"
                onClick={() => {
                  setEmail(em);
                  setSenha(DEMO_PASSWORD);
                  setErrors({});
                  setFormError(null);
                  submitRef.current?.focus();
                }}
                className="flex flex-col items-center gap-1 rounded-lg border border-ciano/40 bg-white px-2 py-2.5 font-ui text-xs font-medium text-ink transition-colors hover:border-estrato hover:bg-estelar"
              >
                <Icon className="size-4 text-estrato" aria-hidden />
                {label}
              </button>
            ))}
          </div>
        </div>
      )}
    </AuthLayout>
  );
}
