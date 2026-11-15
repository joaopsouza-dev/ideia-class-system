
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
// Cache leve de disciplinas
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

async function toTurmaResumo(dto: TurmaResponseDTO): Promise<TurmaResumo> {
  let disciplinaCodigo = dto.codigo;
  let disciplinaNome = dto.disciplinaNome ?? "—";

  if (dto.disciplinaId) {
    try {
      const disciplina = await fetchDisciplina(dto.disciplinaId);
      disciplinaCodigo = disciplina.codigo;
      disciplinaNome = disciplina.nome;
    } catch {
      // Mantém os valores de fallback caso a busca falhe
    }
  }

  return {
    id: dto.id,
    nomeTurma: dto.nome,
    semestreAno: `${dto.ano}/${dto.semestre}`,
    disciplinaId: dto.disciplinaId,
    disciplinaCodigo,
    disciplinaNome,
    rotulo: dto.nome,
  };
}

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
    totalMateriais: 0,
  };
}

// ---------------------------------------------------------------------------
// Implementação do AcademicService
// ---------------------------------------------------------------------------

export const httpAcademicService: AcademicService = {
  async listTurmas(_sessao: Sessao): Promise<TurmaDetalhe[]> {
    disciplinaCache.clear();

    const turmas = await httpClient.get<TurmaResponseDTO[]>(
      API_ENDPOINTS.TURMAS.BASE,
    );

    if (!turmas || turmas.length === 0) return [];

    const resultados = await Promise.allSettled(
      turmas.map(async (dto) => {
        let participantes: TurmaUsuarioResponseDTO[] = [];

        try {
          participantes = await httpClient.get<TurmaUsuarioResponseDTO[]>(
            API_ENDPOINTS.TURMAS.PARTICIPANTES(dto.id),
          );
        } catch {
          // Continua com lista vazia caso a busca falhe
        }

        return toTurmaDetalhe(dto, participantes);
      }),
    );

    return resultados
      .filter(
        (r): r is PromiseFulfilledResult<TurmaDetalhe> =>
          r.status === "fulfilled",
      )
      .map((r) => r.value);
  },

  async getTurma(
    _sessao: Sessao,
    turmaId: number,
  ): Promise<TurmaDetalhe> {
    const [dto, participantes] = await Promise.all([
      httpClient.get<TurmaResponseDTO>(
        API_ENDPOINTS.TURMAS.BY_ID(turmaId),
      ),
      httpClient
        .get<TurmaUsuarioResponseDTO[]>(
          API_ENDPOINTS.TURMAS.PARTICIPANTES(turmaId),
        )
        .catch(() => [] as TurmaUsuarioResponseDTO[]),
    ]);

    return toTurmaDetalhe(dto, participantes);
  },

  async listTurmasGerenciaveis(
    _sessao: Sessao,
  ): Promise<TurmaResumo[]> {
    disciplinaCache.clear();

    const turmas = await httpClient.get<TurmaResponseDTO[]>(
      API_ENDPOINTS.TURMAS.BASE,
    );

    if (!turmas || turmas.length === 0) return [];

    const resultados = await Promise.allSettled(
      turmas.map(toTurmaResumo),
    );

    return resultados
      .filter(
        (r): r is PromiseFulfilledResult<TurmaResumo> =>
          r.status === "fulfilled",
      )
      .map((r) => r.value);
  },

  async listUsuarios(_sessao: Sessao) {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Listagem de usuários não está disponível: o backend ainda não possui este endpoint.",
      501,
    );
  },

  async setUsuarioAtivo(
    _sessao: Sessao,
    _usuarioId: number,
    _ativo: boolean,
  ) {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Alteração de status de usuário não está disponível: o backend ainda não possui este endpoint.",
      501,
    );
  },
};
