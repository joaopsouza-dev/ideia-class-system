import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArrowLeft, Save, Upload, X } from "lucide-react";
import { FileDropzone, validateFile } from "@/components/materials/FileDropzone";
import { Button } from "@/components/ui/Button";
import { Card, PageHeader } from "@/components/ui/Card";
import { SelectField, TextAreaField, TextField } from "@/components/ui/Field";
import { ErrorState, Spinner } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useToast } from "@/context/ToastContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { ApiError, errorMessage, services } from "@/services";
import { config } from "@/services/config";
import type { StatusPublicacao, TipoMaterial } from "@/types";
import { cn } from "@/utils/cn";
import { formatBytes } from "@/utils/format";

type Errors = Partial<Record<"titulo" | "descricao" | "turmaId" | "arquivo", string | null>>;

export default function MaterialEditorPage() {
  const { id } = useParams();
  const editing = !!id;
  useDocumentTitle(editing ? "Editar material" : "Novo material");
  const sessao = useSessao();
  const navigate = useNavigate();
  const toast = useToast();
  const [params] = useSearchParams();

  const turmas = useAsync(() => services.academic.listTurmasGerenciaveis(sessao), [sessao]);
  const existing = useAsync(async () => (id ? services.materials.get(sessao, id) : null), [sessao, id]);

  const [titulo, setTitulo] = useState("");
  const [descricao, setDescricao] = useState("");
  const [tipo, setTipo] = useState<TipoMaterial>("PDF");
  const [turmaId, setTurmaId] = useState<number | "">(Number(params.get("turma")) || "");
  const [status, setStatus] = useState<StatusPublicacao>("PUBLICADO");
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [progress, setProgress] = useState<number | null>(null);
  const abort = useRef<AbortController | null>(null);
  const saving = progress !== null;

  useEffect(() => {
    const c = existing.data;
    if (!c) return;
    setTitulo(c.titulo);
    setDescricao(c.descricao);
    setTipo(c.tipo);
    setTurmaId(c.turma.id);
    setStatus(c.status);
  }, [existing.data]);

  // pré-seleciona a turma quando o professor só tem uma
  useEffect(() => {
    if (!editing && turmaId === "" && turmas.data?.length === 1) setTurmaId(turmas.data[0].id);
  }, [turmas.data, editing, turmaId]);

  const maxMb = tipo === "PDF" ? config.maxPdfMb : config.maxVideoMb;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Errors = {
      titulo: titulo.trim().length < 3 ? "Informe um título com ao menos 3 caracteres." : null,
      descricao: descricao.length > 500 ? "A descrição deve ter até 500 caracteres." : null,
      turmaId: turmaId === "" ? "Selecione a turma." : null,
      arquivo: !editing && !arquivo ? "Selecione o arquivo do material." : null,
    };
    if (arquivo) next.arquivo = validateFile(arquivo, tipo, maxMb);
    setErrors(next);
    setFormError(null);
    if (Object.values(next).some(Boolean)) return;

    abort.current = new AbortController();
    setProgress(0);
    const opts = {
      signal: abort.current.signal,
      onProgress: (p: { loaded: number; total: number }) => setProgress(Math.round((p.loaded / p.total) * 100)),
    };
    try {
      if (editing && id) {
        await services.materials.update(sessao, id, { titulo, descricao, turmaId: Number(turmaId), status, arquivo }, opts);
        toast.success("Material atualizado", titulo);
      } else {
        await services.materials.create(
          sessao,
          { titulo, descricao, tipo, turmaId: Number(turmaId), status, arquivo },
          opts,
        );
        toast.success(status === "PUBLICADO" ? "Material publicado" : "Rascunho salvo", titulo);
      }
      navigate("/biblioteca");
    } catch (err) {
      setProgress(null);
      if (err instanceof ApiError && err.code === "ABORTED") toast.info("Envio cancelado");
      else setFormError(errorMessage(err));
    }
  }

  if (editing && existing.loading && !existing.data) return <Spinner />;
  if (editing && existing.error) return <ErrorState message={existing.error} onRetry={existing.reload} />;
  if (turmas.error) return <ErrorState message={turmas.error} onRetry={turmas.reload} />;

  if (turmas.data && turmas.data.length === 0)
    return (
      <ErrorState message="Você não está vinculado como docente a nenhuma turma, então não pode cadastrar materiais." />
    );

  return (
    <>
      <Link to="/biblioteca" className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden /> Voltar para a biblioteca
      </Link>
      <PageHeader
        eyebrow={editing ? "Edição" : "Cadastro"}
        title={editing ? "Editar material" : "Novo material"}
        description="O arquivo é entregue aos alunos somente pelo visualizador protegido, com marca d'água e sem opção de download."
      />
      <form onSubmit={onSubmit} noValidate>
        <Card className="space-y-5">
          {formError && (
            <div role="alert" className="flex items-start justify-between gap-3 rounded-lg border border-danger/30 bg-danger-50 px-3.5 py-3 text-sm font-medium text-danger">
              <span>{formError}</span>
            </div>
          )}

          <fieldset disabled={saving} className="space-y-5 disabled:opacity-80">
            <legend className="sr-only">Dados do material</legend>
            <div className="grid gap-5 md:grid-cols-2">
              <SelectField
                label="Tipo de material"
                value={tipo}
                disabled={editing}
                hint={editing ? "O tipo não pode ser alterado após o cadastro." : undefined}
                onChange={(e) => {
                  setTipo(e.target.value as TipoMaterial);
                  setArquivo(null);
                }}
              >
                <option value="PDF">PDF / Apostila</option>
                <option value="VIDEO">Videoaula</option>
              </SelectField>
              <SelectField
                label="Turma"
                required
                value={turmaId}
                error={errors.turmaId}
                onChange={(e) => {
                  setTurmaId(e.target.value ? Number(e.target.value) : "");
                  setErrors((x) => ({ ...x, turmaId: null }));
                }}
              >
                <option value="">Selecione…</option>
                {turmas.data?.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.rotulo} ({t.semestreAno})
                  </option>
                ))}
              </SelectField>
            </div>
            <TextField label="Título" required value={titulo} error={errors.titulo} onChange={(e) => {
                setTitulo(e.target.value);
                setErrors((x) => ({ ...x, titulo: null }));
              }} maxLength={150} placeholder="Ex.: Apostila — Leis de Kepler" />
            <TextAreaField
              label="Descrição"
              value={descricao}
              error={errors.descricao}
              onChange={(e) => {
                setDescricao(e.target.value);
                setErrors((x) => ({ ...x, descricao: null }));
              }}
              hint={`${descricao.length}/500 caracteres`}
              placeholder="Resuma o conteúdo para os alunos."
            />
            <FileDropzone
              tipo={tipo}
              file={arquivo}
              onFile={(f) => {
                setArquivo(f);
                setErrors((x) => ({ ...x, arquivo: null }));
              }}
              maxMb={maxMb}
              error={errors.arquivo}
              disabled={saving}
              currentName={editing ? "arquivo já enviado" : undefined}
            />

            <div>
              <span className="font-ui text-[13px] font-medium">Publicação</span>
              <div className="mt-1.5 grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Publicação">
                {(
                  [
                    ["PUBLICADO", "Publicar agora", "Visível para os alunos da turma."],
                    ["RASCUNHO", "Salvar como rascunho", "Visível apenas para você."],
                  ] as const
                ).map(([value, title, hint]) => (
                  <label
                    key={value}
                    className={cn(
                      "flex cursor-pointer items-start gap-3 rounded border p-3.5 transition-colors",
                      status === value ? "border-acao bg-magenta-50" : "border-line hover:border-estrato/40",
                    )}
                  >
                    <input
                      type="radio"
                      name="status"
                      value={value}
                      checked={status === value}
                      onChange={() => setStatus(value)}
                      className="mt-1 accent-acao"
                    />
                    <span>
                      <span className="block text-sm font-semibold">{title}</span>
                      <span className="block text-xs text-ink-soft">{hint}</span>
                    </span>
                  </label>
                ))}
              </div>
            </div>
          </fieldset>

          {saving && (
            <div role="status" aria-live="polite" className="rounded border border-ciano/50 bg-ciano-50 p-4">
              <div className="flex items-center justify-between text-sm font-semibold">
                <span>Enviando{arquivo ? ` ${arquivo.name}` : ""}…</span>
                <span className="tabular-nums">{progress}%</span>
              </div>
              <div className="mt-2 h-2 overflow-hidden rounded-full bg-white" role="progressbar" aria-valuenow={progress ?? 0} aria-valuemin={0} aria-valuemax={100}>
                <div className="h-full rounded-full bg-acao transition-all" style={{ width: `${progress}%` }} />
              </div>
              <p className="mt-2 text-xs text-ink-soft">
                {arquivo ? formatBytes(arquivo.size) : ""} · envio simulado no navegador (nenhum arquivo sai deste dispositivo).
              </p>
              <Button variant="secondary" size="sm" className="mt-3" icon={<X className="size-4" aria-hidden />} onClick={() => abort.current?.abort()}>
                Cancelar envio
              </Button>
            </div>
          )}

          <div className="flex flex-wrap justify-end gap-2 border-t border-line pt-5">
            <Button variant="secondary" onClick={() => navigate("/biblioteca")} disabled={saving}>
              Cancelar
            </Button>
            <Button
              type="submit"
              loading={saving}
              icon={editing ? <Save className="size-4" aria-hidden /> : <Upload className="size-4" aria-hidden />}
            >
              {editing ? "Salvar alterações" : status === "PUBLICADO" ? "Enviar e publicar" : "Salvar rascunho"}
            </Button>
          </div>
        </Card>
      </form>
    </>
  );
}
