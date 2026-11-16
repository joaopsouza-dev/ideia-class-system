import { useCallback, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, CalendarDays, FlaskConical, Info, UserRound } from "lucide-react";
import { MaterialCover, detalheMidia, tipoLabel } from "@/components/materials/MaterialCard";
import { PdfViewer } from "@/components/viewer/PdfViewer";
import { VideoPlayer } from "@/components/viewer/VideoPlayer";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { ErrorState, Spinner } from "@/components/ui/States";
import { useSessao } from "@/context/AuthContext";
import { useAsync } from "@/hooks/useAsync";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { readProgress, useProgressWriter } from "@/hooks/useProgress";
import { services } from "@/services";
import { formatDate } from "@/utils/format";

export default function MaterialViewerPage() {
  const { id = "" } = useParams();
  const sessao = useSessao();
  const navigate = useNavigate();
  const material = useAsync(() => services.materials.get(sessao, id), [sessao, id]);
  const source = useAsync(() => services.content.getViewerSource(sessao, id), [sessao, id]);
  const saveProgress = useProgressWriter(sessao.usuario.id);
  const [pdfTotal, setPdfTotal] = useState<number | null>(null);
  useDocumentTitle(material.data?.titulo ?? "Material");

  const initial = useMemo(() => readProgress(sessao.usuario.id)[id], [sessao.usuario.id, id]);
  const onPage = useCallback((p: number, total: number) => saveProgress(id, p, total), [id, saveProgress]);
  const onTime = useCallback((t: number, d: number) => saveProgress(id, t, d), [id, saveProgress]);

  const back = () => (window.history.length > 1 ? navigate(-1) : navigate("/biblioteca"));

  if ((material.loading && !material.data) || (source.loading && !source.data)) return <Spinner label="Abrindo material protegido…" />;
  if (material.error) return <ErrorState message={material.error} onRetry={() => navigate("/biblioteca")} />;
  const c = material.data;
  if (!c) return null;

  return (
    <>
      <button type="button" onClick={back} className="mb-4 inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden /> Voltar
      </button>

      <header className="mb-5 flex items-start gap-4">
        <MaterialCover c={c} />
        <div className="min-w-0">
          <p className="lbl text-acao-700">
            {tipoLabel(c.tipo)} · {c.turma.rotulo}
          </p>
          <h1 className="text-3xl font-extrabold tracking-tight sm:text-4xl">{c.titulo}</h1>
        </div>
      </header>


      {source.error || !source.data ? (
        <ErrorState message={source.error ?? "Fonte indisponível."} onRetry={source.reload} />
      ) : c.tipo === "PDF" ? (
        <PdfViewer
          url={source.data.url}
          sessao={sessao}
          conteudoId={c.id}
          initialPage={initial?.posicao ? Math.round(initial.posicao) : 1}
          onPageChange={onPage}
          onLoaded={setPdfTotal}
        />
      ) : (
        <VideoPlayer
          src={source.data.url}
          poster={source.data.posterUrl}
          title={c.titulo}
          sessao={sessao}
          conteudoId={c.id}
          initialTime={initial?.posicao}
          onTime={onTime}
        />
      )}

      <div className="mt-6 grid gap-4 lg:grid-cols-[1fr_20rem]">
        <Card>
          <h2 className="text-[22px]">Sobre este material</h2>
          <p className="mt-2 text-sm text-ink-soft">{c.descricao || "Sem descrição."}</p>
          <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
            <div className="flex items-center gap-2">
              <UserRound className="size-4 text-ink-mute" strokeWidth={1.75} aria-hidden />
              <div>
                <dt className="text-xs text-ink-soft">Professor</dt>
                <dd className="font-medium">{c.professor.nome}</dd>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <CalendarDays className="size-4 text-ink-mute" strokeWidth={1.75} aria-hidden />
              <div>
                <dt className="text-xs text-ink-soft">Criado em</dt>
                <dd className="font-medium">{formatDate(c.criadoEm)}</dd>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <Info className="size-4 text-ink-mute" strokeWidth={1.75} aria-hidden />
              <div>
                <dt className="text-xs text-ink-soft">Detalhes</dt>
                <dd className="font-medium">{c.tipo === "PDF" && pdfTotal ? `${pdfTotal} págs` : detalheMidia(c)}</dd>
              </div>
            </div>
          </dl>
          <div className="mt-4 flex flex-wrap gap-2">
            <Badge tone="ciano">{c.turma.disciplinaNome}</Badge>
            {source.data?.origem === "DEMO" && (
              <Badge tone="nebulosa" icon={<FlaskConical className="size-3" aria-hidden />}>
                Arquivo de demonstração
              </Badge>
            )}
            {source.data?.origem === "UPLOAD_LOCAL" && (
              <Badge tone="nebulosa" icon={<FlaskConical className="size-3" aria-hidden />}>
                Enviado nesta sessão (apenas neste navegador)
              </Badge>
            )}
          </div>
        </Card>

      </div>
    </>
  );
}
