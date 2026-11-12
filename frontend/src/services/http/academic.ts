/**
 * Adapter HTTP real para AcademicService.
 *
 * Implementa o contrato AcademicService usando exclusivamente os endpoints
 * confirmados no backend Spring Boot:
 *   - GET /api/v1/turmas            → lista turmas (filtradas pelo RLS do banco)
 *   - GET /api/v1/turmas/{id}       → detalhe de uma turma
 *   - GET /api/v1/turmas/{id}/participantes → participantes da turma
 *   - GET /api/v1/disciplinas/{id}  → dados da disciplina (código)
 *
 * Endpoints NÃO existentes no backend (retornam NOT_IMPLEMENTED):
 *   - listUsuarios  → não há endpoint de listagem de usuários
 *   - setUsuarioAtivo → não há endpoint de ativação/desativação de usuários
 */

import type { AcademicService } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS } from "../api";
import type {
  DisciplinaResponseDTO,
  TurmaResponseDTO,
  TurmaUsuarioResponseDTO,
} from "../api/types";
import type { Sessao, TurmaDetalhe, TurmaResumo } from "@/types";

// ---------------------------------------------------------------------------
// Cache leve de disciplinas (válido apenas durante a vida da requisição).
// Evita chamar GET /api/v1/disciplinas/{id} repetidamente para a mesma disciplina
// quando listTurmas retorna várias turmas da mesma disciplina.
// ---------------------------------------------------------------------------
const disciplinaCache = new Map<number, DisciplinaResponseDTO>();

async function fetchDisciplina(id: number): Promise<DisciplinaResponseDTO> {
  const cached = disciplinaCache.get(id);
  if (cached) return cached;

  const d = await httpClient.get<DisciplinaResponseDTO>(
    API_ENDPOINTS.DISCIPLINAS.BY_ID(id),
  );
  disciplinaCache.set(id, d);
  return d;
}

// ---------------------------------------------------------------------------
// Mapeadores: TurmaResponseDTO → TurmaResumo / TurmaDetalhe
// ---------------------------------------------------------------------------

/**
 * Monta TurmaResumo a partir do DTO do backend.
 * Busca o código da disciplina via GET /api/v1/disciplinas/{id}
 * pois TurmaResponseDTO não inclui esse campo.
 */
async function toTurmaResumo(dto: TurmaResponseDTO): Promise<TurmaResumo> {
  // disciplinaNome já vem no DTO; buscamos a disciplina para obter o código
  let disciplinaCodigo = dto.codigo; // fallback: usa o código da turma
  let disciplinaNome = dto.disciplinaNome ?? "—";

  if (dto.disciplinaId) {
    try {
      const disciplina = await fetchDisciplina(dto.disciplinaId);
      disciplinaCodigo = disciplina.codigo;
      disciplinaNome = disciplina.nome;
    } catch {
      // Se a busca da disciplina falhar, mantém os valores de fallback
    }
  }

  return {
    id: dto.id,
    nomeTurma: dto.nome,
    semestreAno: `${dto.ano}/${dto.semestre}`,
    disciplinaId: dto.disciplinaId,
    disciplinaCodigo,
    disciplinaNome,
    rotulo: `${disciplinaNome} · ${dto.nome}`,
  };
}

/**
 * Monta TurmaDetalhe: resumo + participantes separados por papel.
 * Chama GET /api/v1/turmas/{id}/participantes.
 * totalMateriais é 0 pois não existe endpoint de contagem no backend atual.
 */
async function toTurmaDetalhe(
  dto: TurmaResponseDTO,
  participantes: TurmaUsuarioResponseDTO[],
): Promise<TurmaDetalhe> {
  const resumo = await toTurmaResumo(dto);

  const docentes = participantes
    .filter((p) => p.papel === "DOCENTE" && p.status === "ATIVA")
    .map((p) => ({ id: p.usuarioId, nome: p.usuarioNome }));

  const totalAlunos = participantes.filter(
    (p) => p.papel === "ALUNO" && p.status === "ATIVA",
  ).length;

  return {
    ...resumo,
    docentes,
    totalAlunos,
    totalMateriais: 0, // endpoint de contagem de materiais não existe no backend
  };
}

// ---------------------------------------------------------------------------
// Implementação do AcademicService
// ---------------------------------------------------------------------------

export const httpAcademicService: AcademicService = {
  /**
   * Lista todas as turmas visíveis ao usuário autenticado.
   * O RLS do PostgreSQL filtra automaticamente conforme o perfil:
   *   - ADMIN: vê todas
   *   - DOCENTE: vê as que leciona
   *   - ALUNO: vê as que está matriculado
   *
   * Para cada turma, busca também os participantes para montar docentes e totalAlunos.
   * Requisições de participantes são executadas em paralelo.
   */
  async listTurmas(_sessao: Sessao): Promise<TurmaDetalhe[]> {
    // Limpa cache de disciplinas a cada nova listagem completa
    disciplinaCache.clear();

    const turmas = await httpClient.get<TurmaResponseDTO[]>(
      API_ENDPOINTS.TURMAS.BASE,
    );

    if (!turmas || turmas.length === 0) return [];

    // Busca participantes de cada turma em paralelo
    const resultados = await Promise.allSettled(
      turmas.map(async (dto) => {
        let participantes: TurmaUsuarioResponseDTO[] = [];
        try {
          participantes = await httpClient.get<TurmaUsuarioResponseDTO[]>(
            API_ENDPOINTS.TURMAS.PARTICIPANTES(dto.id),
          );
        } catch {
          // Se falhar ao buscar participantes, continua com lista vazia
        }
        return toTurmaDetalhe(dto, participantes);
      }),
    );

    return resultados
      .filter((r): r is PromiseFulfilledResult<TurmaDetalhe> => r.status === "fulfilled")
      .map((r) => r.value);
  },

  /**
   * Busca o detalhe de uma turma específica.
   * Faz duas chamadas em paralelo: turma e participantes.
   */
  async getTurma(_sessao: Sessao, turmaId: number): Promise<TurmaDetalhe> {
    const [dto, participantes] = await Promise.all([
      httpClient.get<TurmaResponseDTO>(API_ENDPOINTS.TURMAS.BY_ID(turmaId)),
      httpClient
        .get<TurmaUsuarioResponseDTO[]>(API_ENDPOINTS.TURMAS.PARTICIPANTES(turmaId))
        .catch(() => [] as TurmaUsuarioResponseDTO[]),
    ]);

    return toTurmaDetalhe(dto, participantes);
  },

  /**
   * Lista turmas onde o usuário pode publicar conteúdo (gerenciáveis).
   * Usa o mesmo endpoint GET /api/v1/turmas — o RLS já filtra.
   * Retorna TurmaResumo (sem docentes/totalAlunos/totalMateriais).
   */
  async listTurmasGerenciaveis(_sessao: Sessao): Promise<TurmaResumo[]> {
    disciplinaCache.clear();

    const turmas = await httpClient.get<TurmaResponseDTO[]>(
      API_ENDPOINTS.TURMAS.BASE,
    );

    if (!turmas || turmas.length === 0) return [];

    const resultados = await Promise.allSettled(turmas.map(toTurmaResumo));

    return resultados
      .filter((r): r is PromiseFulfilledResult<TurmaResumo> => r.status === "fulfilled")
      .map((r) => r.value);
  },

  /**
   * NÃO IMPLEMENTADO: o backend não possui endpoint de listagem de usuários.
   * Lança ApiError com código NOT_IMPLEMENTED para sinalizar ausência da funcionalidade.
   */
  async listUsuarios(_sessao: Sessao) {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Listagem de usuários não está disponível: o backend ainda não possui este endpoint.",
      501,
    );
  },

  /**
   * NÃO IMPLEMENTADO: o backend não possui endpoint de ativação/desativação de usuários.
   */
  async setUsuarioAtivo(_sessao: Sessao, _usuarioId: number, _ativo: boolean) {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Alteração de status de usuário não está disponível: o backend ainda não possui este endpoint.",
      501,
    );
  },
};
