/**
 * Adapter HTTP real para ContentAccessService (visualização protegida).
 *
 *   GET /api/v1/conteudos/{id}/acesso  ->  { url, expiraEm, tipo, mimeType }
 *
 * O backend valida a autorização (aluno: material PUBLICADO da própria turma; docente autor;
 * admin), gera uma URL temporária assinada e registra ABRIU_PDF / INICIOU_VIDEO com o IP e o
 * User-Agent da requisição. Sem permissão ou material inexistente: 404 (mesma resposta).
 *
 * O frontend nunca conhece o caminho do objeto no storage; a URL recebida vale só por alguns
 * minutos e não deve ser guardada.
 */

import type { ContentAccessService } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS } from "../api";
import type { AcessoConteudoResponseDTO } from "../api/types";
import type { Sessao, ViewerSource } from "@/types";

/**
 * Cada chamada gera um evento de auditoria no servidor. Chamadas idênticas feitas ao mesmo tempo
 * (ex.: efeito duplo do React.StrictMode em desenvolvimento) compartilham a mesma requisição,
 * evitando eventos duplicados para uma única abertura.
 */
const emAndamento = new Map<string, Promise<ViewerSource>>();

export const httpContentAccessService: ContentAccessService = {
  async getViewerSource(sessao: Sessao, conteudoId: string): Promise<ViewerSource> {
    if (!conteudoId || conteudoId.trim() === "") {
      throw new ApiError("VALIDATION", "O ID do material é obrigatório.", 400);
    }

    const chave = `${sessao.usuario.id}:${conteudoId}`;
    const pendente = emAndamento.get(chave);
    if (pendente) return pendente;

    const requisicao = (async (): Promise<ViewerSource> => {
      const dto = await httpClient.get<AcessoConteudoResponseDTO>(
        API_ENDPOINTS.CONTEUDOS.ACESSO(conteudoId),
      );
      return {
        url: httpClient.resolveUrl(dto.url),
        origem: "API",
        expiraEm: dto.expiraEm,
      };
    })().finally(() => {
      emAndamento.delete(chave);
    });

    emAndamento.set(chave, requisicao);
    return requisicao;
  },
};
