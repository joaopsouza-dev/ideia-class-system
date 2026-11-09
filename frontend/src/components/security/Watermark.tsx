import { useEffect, useMemo, useState } from "react";
import type { Sessao } from "@/types";
import { formatDateTime } from "@/utils/format";

interface Props {
  sessao: Sessao;
  /** tom do fundo sobre o qual a marca é desenhada */
  on?: "light" | "dark";
}

/**
 * Marca d'água dinâmica: nome, e-mail, ID de sessão e data/hora do usuário autenticado.
 * - Padrão repetido em toda a área (inclusive em tela cheia, pois vive dentro do contêiner).
 * - Um selo adicional "salta" de posição periodicamente (dificulta recortes).
 * Recebe dados reais da sessão; hoje a sessão é de demonstração.
 */
export function Watermark({ sessao, on = "light" }: Props) {
  const [now, setNow] = useState(() => new Date().toISOString());
  const [pos, setPos] = useState({ top: 18, left: 12 });

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date().toISOString()), 30_000);
    const hop = setInterval(
      () => setPos({ top: 8 + Math.random() * 78, left: 4 + Math.random() * 62 }),
      9000,
    );
    return () => {
      clearInterval(clock);
      clearInterval(hop);
    };
  }, []);

  const u = sessao.usuario;
  const line1 = `${u.nomeCompleto} · ${u.email}`;
  const line2 = `Sessão ${sessao.sessionId} · ${formatDateTime(now)}`;

  const tile = useMemo(() => {
    const fill = on === "light" ? "#1d1856" : "#ffffff";
    const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;");
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="440" height="230"><g transform="rotate(-24 220 115)" fill="${fill}" fill-opacity="0.16" font-family="Montserrat,Arial,sans-serif"><text x="30" y="108" font-size="14" font-weight="700">${esc(line1)}</text><text x="30" y="128" font-size="12">${esc(line2)}</text></g></svg>`;
    return `url("data:image/svg+xml;utf8,${encodeURIComponent(svg)}")`;
  }, [line1, line2, on]);

  return (
    <div
      data-watermark="true"
      aria-hidden
      className="pointer-events-none absolute inset-0 z-30 overflow-hidden"
      style={{ backgroundImage: tile, backgroundRepeat: "repeat" }}
    >
      <span
        className="absolute max-w-[70%] rounded-md px-2 py-1 font-ui text-[11px] font-semibold leading-tight transition-all duration-1000"
        style={{
          top: `${pos.top}%`,
          left: `${pos.left}%`,
          color: on === "light" ? "rgba(29,24,86,.55)" : "rgba(255,255,255,.6)",
          background: on === "light" ? "rgba(255,255,255,.35)" : "rgba(12,9,49,.35)",
          animation: "wm-hop 4s ease-in-out infinite",
        }}
      >
        {u.nomeCompleto}
        <br />
        {line2}
      </span>
    </div>
  );
}
