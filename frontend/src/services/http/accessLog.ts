import type { AccessLogService } from "../contracts";
import { httpClient, API_ENDPOINTS } from "../api";
import type { AuditoriaResponseDTO, BackendAcaoEventoCliente, EventoAcessoRequestDTO } from "../api/types";
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
 * Eventos que o navegador detecta e que têm equivalente no enum acao_log_enum do banco.
 * Captura de tela (PrintScreen) e impressão são tentativas de "print": ambas viram
 * TENTATIVA_PRINT_DETECTADA, e o tipo original segue no detalhe. Os demais eventos da interface
 * (cópia, perda de foco, marca d'água alterada...) NÃO têm ação correspondente no banco e não
 * são persistidos. Abertura de PDF/vídeo e acesso negado são registrados pelo servidor.
 */
const EVENTO_REPORTAVEL: Partial<Record<TipoAcao, { acao: BackendAcaoEventoCliente; rotulo?: string }>> = {
  TENTATIVA_DOWNLOAD: { acao: "TENTATIVA_DOWNLOAD" },
  TENTATIVA_IMPRESSAO: { acao: "TENTATIVA_PRINT_DETECTADA", rotulo: "Impressão" },
  TENTATIVA_CAPTURA: { acao: "TENTATIVA_PRINT_DETECTADA", rotulo: "Captura de tela" },
};

/**
 * Adapter HTTP de auditoria. Lista somente registros reais do banco (RLS: admin vê todos).
 *
 * record(): envia a tentativa a POST /api/v1/conteudos/{id}/eventos. O backend determina o
 * usuário (JWT), o IP e o User-Agent; o frontend só informa a ação e um texto curto.
 */
export const httpAccessLogService: AccessLogService = {
  async list(_sessao: Sessao): Promise<LogAcesso[]> {
    const lista = await httpClient.get<AuditoriaResponseDTO[]>(API_ENDPOINTS.AUDITORIA.BASE);
    return lista.map(toLogAcesso);
  },

  async record(_sessao: Sessao, evento: RegistroAcessoInput): Promise<void> {
    const alvo = EVENTO_REPORTAVEL[evento.tipoAcao];
    if (!alvo || !evento.conteudoId) return; // sem equivalente no banco: não persistido

    const detalhe = [alvo.rotulo, evento.detalhe].filter(Boolean).join(": ").slice(0, 200);
    const corpo: EventoAcessoRequestDTO = { acao: alvo.acao, ...(detalhe ? { detalhe } : {}) };
    await httpClient.post<void>(API_ENDPOINTS.CONTEUDOS.EVENTOS(evento.conteudoId), corpo);
  },
};
