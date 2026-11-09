import { useCallback } from "react";

/**
 * Progresso de leitura/visualização guardado no navegador (localStorage).
 * É um recurso REAL do frontend, mas local ao dispositivo: quando o backend
 * oferecer histórico por usuário, troque a implementação (ver docs/API_CONTRACT.md).
 */
export interface ProgressEntry {
  conteudoId: string;
  /** página (PDF) ou segundos (vídeo) */
  posicao: number;
  total: number;
  atualizadoEm: string;
}

const key = (userId: number) => `ideiaspace.progress.${userId}`;

export function readProgress(userId: number): Record<string, ProgressEntry> {
  try {
    return JSON.parse(localStorage.getItem(key(userId)) ?? "{}") as Record<string, ProgressEntry>;
  } catch {
    return {};
  }
}

export function useProgressWriter(userId: number) {
  return useCallback(
    (conteudoId: string, posicao: number, total: number) => {
      try {
        const all = readProgress(userId);
        all[conteudoId] = { conteudoId, posicao, total, atualizadoEm: new Date().toISOString() };
        localStorage.setItem(key(userId), JSON.stringify(all));
      } catch {
        /* armazenamento indisponível: o recurso simplesmente não persiste */
      }
    },
    [userId],
  );
}
