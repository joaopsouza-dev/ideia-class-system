import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, Maximize, Minimize, Pause, Play, RotateCcw, Volume2, VolumeX } from "lucide-react";
import { Button, IconButton } from "@/components/ui/Button";
import { ProtectedSurface } from "@/components/security/ProtectedSurface";
import type { Sessao } from "@/types";
import { cn } from "@/utils/cn";
import { formatDuration } from "@/utils/format";

const SPEEDS = [0.75, 1, 1.25, 1.5, 2];

interface Props {
  src: string;
  poster?: string;
  title: string;
  sessao: Sessao;
  conteudoId: string;
  initialTime?: number;
  onTime?: (current: number, duration: number) => void;
}

/**
 * Player próprio. Recebe uma URL de streaming (fornecida pelo backend). Remove os
 * controles nativos (e o item "Salvar vídeo como…") e aplica marca d'água dinâmica.
 * Observação: se o backend entregar HLS/DASH, será necessário um adaptador
 * (ex.: hls.js) — ver docs/API_CONTRACT.md.
 */
export function VideoPlayer({ src, poster, title, sessao, conteudoId, initialTime = 0, onTime }: Props) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const lastReport = useRef(0);
  const idle = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [playing, setPlaying] = useState(false);
  const [current, setCurrent] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [speedIdx, setSpeedIdx] = useState(1);
  const [buffering, setBuffering] = useState(true);
  const [error, setError] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [showControls, setShowControls] = useState(true);
  const [ended, setEnded] = useState(false);
  const [attempt, setAttempt] = useState(0);

  const poke = useCallback(() => {
    setShowControls(true);
    clearTimeout(idle.current);
    idle.current = setTimeout(() => setShowControls(false), 3000);
  }, []);
  useEffect(() => () => clearTimeout(idle.current), []);

  useEffect(() => {
    const onFs = () => setFullscreen(document.fullscreenElement === wrapRef.current);
    document.addEventListener("fullscreenchange", onFs);
    return () => document.removeEventListener("fullscreenchange", onFs);
  }, []);

  const toggle = useCallback(() => {
    const v = videoRef.current;
    if (!v) return;
    if (v.paused) void v.play().catch(() => undefined);
    else v.pause();
  }, []);

  function seek(t: number) {
    const v = videoRef.current;
    if (!v) return;
    v.currentTime = Math.min(Math.max(0, t), duration || t);
    setCurrent(v.currentTime);
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void wrapRef.current?.requestFullscreen?.();
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.target as HTMLElement).tagName === "INPUT" || (e.target as HTMLElement).tagName === "BUTTON") {
      if (e.key !== "f" && e.key !== "m") return;
    }
    switch (e.key) {
      case " ":
      case "k":
        toggle();
        break;
      case "ArrowRight":
        seek(current + 5);
        break;
      case "ArrowLeft":
        seek(current - 5);
        break;
      case "ArrowUp":
        changeVolume(Math.min(1, volume + 0.1));
        break;
      case "ArrowDown":
        changeVolume(Math.max(0, volume - 0.1));
        break;
      case "m":
        setMuted((m) => !m);
        break;
      case "f":
        toggleFullscreen();
        break;
      default:
        return;
    }
    e.preventDefault();
    poke();
  }

  function changeVolume(v: number) {
    setVolume(v);
    setMuted(v === 0);
    if (videoRef.current) videoRef.current.volume = v;
  }

  const pct = duration ? (current / duration) * 100 : 0;
  const volPct = (muted ? 0 : volume) * 100;
  const speed = SPEEDS[speedIdx];
  useEffect(() => {
    if (videoRef.current) videoRef.current.playbackRate = speed;
  }, [speed]);

  return (
    <div
      ref={wrapRef}
      tabIndex={0}
      onKeyDown={onKeyDown}
      onMouseMove={poke}
      aria-label={`Player de vídeo: ${title}`}
      className={cn("overflow-hidden rounded-2xl bg-black outline-offset-4", fullscreen && "rounded-none")}
    >
      <ProtectedSurface
        sessao={sessao}
        conteudoId={conteudoId}
        on="dark"
        className={cn(fullscreen ? "h-dvh" : "aspect-video")}
        onObscuredChange={(o) => o && videoRef.current?.pause()}
      >
        <video
          key={attempt}
          ref={videoRef}
          src={src}
          poster={poster}
          preload="metadata"
          playsInline
          controlsList="nodownload noremoteplayback noplaybackrate"
          disablePictureInPicture
          disableRemotePlayback
          onContextMenu={(e) => e.preventDefault()}
          onClick={toggle}
          onLoadedMetadata={(e) => {
            const v = e.currentTarget;
            setDuration(v.duration);
            setBuffering(false);
            if (initialTime > 0 && initialTime < v.duration - 3) v.currentTime = initialTime;
          }}
          onTimeUpdate={(e) => {
            const v = e.currentTarget;
            setCurrent(v.currentTime);
            if (Date.now() - lastReport.current > 4000) {
              lastReport.current = Date.now();
              onTime?.(v.currentTime, v.duration);
            }
          }}
          onPlay={() => {
            setPlaying(true);
            setEnded(false);
            poke();
          }}
          onPause={(e) => {
            setPlaying(false);
            setShowControls(true);
            onTime?.(e.currentTarget.currentTime, e.currentTarget.duration);
          }}
          onEnded={(e) => {
            setEnded(true);
            setPlaying(false);
            onTime?.(e.currentTarget.duration, e.currentTarget.duration);
          }}
          onWaiting={() => setBuffering(true)}
          onCanPlay={() => setBuffering(false)}
          onPlaying={() => setBuffering(false)}
          onError={() => {
            setError(true);
            setBuffering(false);
          }}
          className="absolute inset-0 h-full w-full bg-black object-contain"
        />

        {buffering && !error && (
          <div className="pointer-events-none absolute inset-0 z-[32] grid place-items-center">
            <Loader2 className="size-10 animate-spin text-white/80" aria-label="Carregando vídeo" />
          </div>
        )}

        {!playing && !error && !buffering && (
          <button
            type="button"
            onClick={toggle}
            aria-label={ended ? "Assistir novamente" : "Reproduzir"}
            className="absolute inset-0 z-[33] grid place-items-center"
          >
            <span className="grid size-20 place-items-center rounded-full bg-magenta text-white shadow-pop transition-transform hover:scale-105">
              {ended ? <RotateCcw className="size-8" aria-hidden /> : <Play className="ml-1 size-9" aria-hidden />}
            </span>
          </button>
        )}

        {error && (
          <div role="alert" className="absolute inset-0 z-[36] grid place-items-center bg-vacuo p-6 text-center text-white">
            <div>
              <p className="text-lg font-bold">Não foi possível reproduzir o vídeo</p>
              <p className="mx-auto mt-2 max-w-md text-sm text-white/70">
                O link de streaming pode ter expirado ou o arquivo está indisponível.
              </p>
              <Button
                className="mt-5"
                onClick={() => {
                  setError(false);
                  setBuffering(true);
                  setAttempt((a) => a + 1);
                }}
              >
                Tentar novamente
              </Button>
            </div>
          </div>
        )}

        <div
          className={cn(
            "absolute inset-x-0 bottom-0 z-[35] bg-gradient-to-t from-black/85 to-transparent px-3 pb-2 pt-10 text-white transition-opacity",
            showControls || !playing ? "opacity-100" : "pointer-events-none opacity-0",
          )}
          onFocus={() => setShowControls(true)}
        >
          <input
            type="range"
            className="seek"
            min={0}
            max={duration || 0}
            step={0.1}
            value={current}
            disabled={!duration}
            aria-label="Progresso do vídeo"
            aria-valuetext={`${formatDuration(current)} de ${formatDuration(duration)}`}
            style={{ ["--pct" as string]: `${pct}%` }}
            onChange={(e) => seek(Number(e.target.value))}
          />
          <div className="flex items-center gap-1">
            <IconButton label={playing ? "Pausar" : "Reproduzir"} onClick={toggle} className="text-white hover:bg-white/15 hover:text-white">
              {playing ? <Pause className="size-5" aria-hidden /> : <Play className="size-5" aria-hidden />}
            </IconButton>
            <IconButton label={muted ? "Ativar som" : "Silenciar"} onClick={() => setMuted((m) => !m)} className="text-white hover:bg-white/15 hover:text-white">
              {muted || volume === 0 ? <VolumeX className="size-5" aria-hidden /> : <Volume2 className="size-5" aria-hidden />}
            </IconButton>
            <input
              type="range"
              className="seek hidden w-24 sm:block"
              min={0}
              max={1}
              step={0.05}
              value={muted ? 0 : volume}
              aria-label="Volume"
              style={{ ["--pct" as string]: `${volPct}%` }}
              onChange={(e) => changeVolume(Number(e.target.value))}
            />
            <span className="ml-2 whitespace-nowrap font-ui text-xs tabular-nums text-white/90">
              {formatDuration(current)} / {formatDuration(duration)}
            </span>
            <span className="flex-1" />
            <button
              type="button"
              onClick={() => setSpeedIdx((i) => (i + 1) % SPEEDS.length)}
              className="h-8 rounded-md px-2 font-ui text-xs font-semibold text-white hover:bg-white/15"
              aria-label={`Velocidade de reprodução: ${speed}x. Alterar`}
            >
              {speed}x
            </button>
            <IconButton label={fullscreen ? "Sair da tela cheia" : "Tela cheia"} onClick={toggleFullscreen} className="text-white hover:bg-white/15 hover:text-white">
              {fullscreen ? <Minimize className="size-5" aria-hidden /> : <Maximize className="size-5" aria-hidden />}
            </IconButton>
          </div>
        </div>
      </ProtectedSurface>
    </div>
  );
}
