
import type { AcademicService } from "../contracts";
import { httpClient, API_ENDPOINTS } from "../api";
import type {
  AlunoDisponivelResponseDTO,
  DisciplinaResponseDTO,
  TurmaResponseDTO,
  TurmaUsuarioResponseDTO,
  UsuarioAdminResponseDTO,
} from "../api/types";
import type {
  AlunoDisponivel,
  AtualizarUsuarioInput,
  ParticipanteTurma,
  Sessao,
  TurmaDetalhe,
  TurmaResumo,
  UsuarioAdmin,
} from "@/types";
import { normalizarPerfil } from "./auth";

// ---------------------------------------------------------------------------
// Mapeador: UsuarioAdminResponseDTO → UsuarioAdmin
// ---------------------------------------------------------------------------
function toUsuarioAdmin(dto: UsuarioAdminResponseDTO): UsuarioAdmin {
  return {
    id: dto.id,
    nomeCompleto: dto.nomeCompleto,
    email: dto.email,
    perfil: normalizarPerfil(dto.perfil),
    statusAtivo: dto.statusConta === "ATIVA",
    criadoEm: dto.criadoEm,
    turmas: dto.turmas.map((t) => ({
      id: t.turmaId,
      rotulo: t.turmaNome,
      papel: t.papel === "DOCENTE" ? "DOCENTE" : "ALUNO",
    })),
  };
}

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
    (p) => p.papel === "DISCENTE" && p.status === "ATIVA",
  ).length;

  return {
    ...resumo,
    docentes,
    totalAlunos,
    totalMateriais: 0,
  };
}

/** Converte o vínculo do backend (papel DISCENTE/DOCENTE) para o modelo da interface. */
function toParticipante(dto: TurmaUsuarioResponseDTO): ParticipanteTurma {
  return {
    vinculoId: dto.id,
    usuarioId: dto.usuarioId,
    nome: dto.usuarioNome,
    email: dto.usuarioEmail,
    papel: dto.papel === "DOCENTE" ? "DOCENTE" : "ALUNO",
    status: dto.status,
  };
}

function toAlunoDisponivel(dto: AlunoDisponivelResponseDTO): AlunoDisponivel {
  return { id: dto.id, nomeCompleto: dto.nomeCompleto, email: dto.email };
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
    const lista = await httpClient.get<UsuarioAdminResponseDTO[]>(
      API_ENDPOINTS.USUARIOS.BASE,
    );
    return lista.map(toUsuarioAdmin);
  },

  async atualizarUsuario(_sessao: Sessao, usuarioId: number, dados: AtualizarUsuarioInput) {
    const corpo: Record<string, string> = {};
    if (dados.nomeCompleto !== undefined) corpo.nomeCompleto = dados.nomeCompleto;
    if (dados.email !== undefined) corpo.email = dados.email;
    if (dados.perfil !== undefined) corpo.perfil = dados.perfil === "PROFESSOR" ? "DOCENTE" : dados.perfil;
    const dto = await httpClient.patch<UsuarioAdminResponseDTO>(API_ENDPOINTS.USUARIOS.BY_ID(usuarioId), corpo);
    return toUsuarioAdmin(dto);
  },

  async listParticipantes(_sessao: Sessao, turmaId: number): Promise<ParticipanteTurma[]> {
    const lista = await httpClient.get<TurmaUsuarioResponseDTO[]>(
      API_ENDPOINTS.TURMAS.PARTICIPANTES(turmaId),
    );
    return lista.map(toParticipante);
  },

  async listAlunosDisponiveis(_sessao: Sessao): Promise<AlunoDisponivel[]> {
    const lista = await httpClient.get<AlunoDisponivelResponseDTO[]>(API_ENDPOINTS.USUARIOS.ALUNOS);
    return lista.map(toAlunoDisponivel);
  },

  async vincularAluno(_sessao: Sessao, turmaId: number, usuarioId: number): Promise<ParticipanteTurma> {
    const dto = await httpClient.post<TurmaUsuarioResponseDTO>(
      API_ENDPOINTS.TURMAS.PARTICIPANTES(turmaId),
      { usuarioId, papel: "DISCENTE" },
    );
    return toParticipante(dto);
  },

  async removerParticipante(_sessao: Sessao, turmaId: number, vinculoId: number): Promise<void> {
    await httpClient.delete<void>(API_ENDPOINTS.TURMAS.REMOVER_PARTICIPANTE(turmaId, vinculoId));
  },

  async setUsuarioAtivo(_sessao: Sessao, usuarioId: number, ativo: boolean) {
    await httpClient.patch<UsuarioAdminResponseDTO>(
      API_ENDPOINTS.USUARIOS.STATUS(usuarioId),
      { ativo },
    );
  },
};
