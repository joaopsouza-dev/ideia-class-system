import type { AccessLogService } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS } from "../api";
import type { AuditoriaResponseDTO } from "../api/types";
import type { LogAcesso, RegistroAcessoInput, Sessao, TipoAcao } from "@/types";

/**
 * Converte a ação persistida em log_acesso (enum acao_log_enum do banco)
 * para o rótulo usado pela interface.
 */
const ACAO_BACKEND_PARA_UI: Record<string, TipoAcao> = {
  ABRIU_PDF: "ABRIU_PDF",
  INICIOU_VIDEO: "ABRIU_VIDEO",
  ASSISTIU_VIDEO: "ABRIU_VIDEO",
  TENTATIVA_DOWNLOAD: "TENTATIVA_DOWNLOAD",
  TENTATIVA_PRINT_DETECTADA: "TENTATIVA_IMPRESSAO",
  TENTATIVA_ACESSO_NEGADO: "TENTATIVA_ACESSO_NEGADO",
  GEROU_URL_TEMPORARIA: "GEROU_URL_TEMPORARIA",
};

function toLogAcesso(dto: AuditoriaResponseDTO): LogAcesso {
  return {
    id: dto.id,
    usuarioId: dto.usuarioId,
    usuarioNome: dto.usuarioNome,
    conteudoId: dto.conteudoId === null ? null : String(dto.conteudoId),
    conteudoTitulo: dto.conteudoTitulo,
    tipoAcao: ACAO_BACKEND_PARA_UI[dto.acao] ?? "TENTATIVA_ACESSO_NEGADO",
    ipOrigem: dto.ipOrigem ?? "—",
    dispositivo: dto.userAgent ?? "—",
    dataHora: dto.criadoEm,
  };
}

/**
 * Adapter HTTP de auditoria. Lista somente registros reais do banco (RLS: admin vê todos).
 *
 * record(): a gravação de eventos feitos na visualização protegida ainda NÃO está ligada
 * ao backend (depende do módulo de visualização, sob responsabilidade de outro desenvolvedor,
 * que deve chamar fn_registrar_acesso). Até lá o evento não é persistido e não aparece na
 * auditoria. Não há simulação local.
 */
export const httpAccessLogService: AccessLogService = {
  async list(_sessao: Sessao): Promise<LogAcesso[]> {
    const lista = await httpClient.get<AuditoriaResponseDTO[]>(API_ENDPOINTS.AUDITORIA.BASE);
    return lista.map(toLogAcesso);
  },

  async record(_sessao: Sessao, _evento: RegistroAcessoInput): Promise<void> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "O registro de eventos da visualização protegida ainda não está ligado ao backend.",
      501,
    );
  },
};
