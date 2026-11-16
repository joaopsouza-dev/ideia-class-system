/**
 * Adapter HTTP real para MaterialService (Etapas 2 e 3).
 *
 * Endpoints do backend (/api/v1/conteudos):
 *   - GET    /                 lista (RLS: aluno não recebe rascunhos)
 *   - GET    /{id}             busca por ID
 *   - POST   /                 cadastro com upload (multipart: campos + "arquivo")
 *   - PUT    /{id}             edição e substituição opcional do arquivo (multipart)
 *   - PATCH  /{id}/status      publicar (PUBLICADO) / despublicar (RASCUNHO)
 *   - DELETE /{id}             exclusão lógica
 *
 * Caminho físico, MIME, tamanho e hash são definidos pelo servidor e nunca trafegam daqui.
 */

import type { MaterialService, UploadProgress } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS } from "../api";
import type {
  ConteudoResponseDTO,
  TurmaResponseDTO,
  DisciplinaResponseDTO,
  TurmaUsuarioResponseDTO,
  BackendTipoMaterial,
} from "../api/types";
import type {
  Conteudo,
  MaterialFiltros,
  MaterialInput,
  Sessao,
  StatusPublicacao,
  TipoMaterial,
  TurmaResumo,
} from "@/types";
import { normalize } from "../../utils/format";

// ---------------------------------------------------------------------------
// Cache leve de turmas e disciplinas para montagem de TurmaResumo
// ---------------------------------------------------------------------------
const turmaResumoCache = new Map<number, Promise<TurmaResumo>>();
const disciplinaCache = new Map<number, Promise<DisciplinaResponseDTO>>();

function fetchDisciplina(id: number): Promise<DisciplinaResponseDTO> {
  const cached = disciplinaCache.get(id);
  if (cached) return cached;

  const promise = httpClient.get<DisciplinaResponseDTO>(
    API_ENDPOINTS.DISCIPLINAS.BY_ID(id),
  );
  disciplinaCache.set(id, promise);
  return promise;
}

function fetchTurmaResumo(turmaId: number): Promise<TurmaResumo> {
  const cached = turmaResumoCache.get(turmaId);
  if (cached) return cached;

  const promise = (async (): Promise<TurmaResumo> => {
    try {
      const dto = await httpClient.get<TurmaResponseDTO>(
        API_ENDPOINTS.TURMAS.BY_ID(turmaId),
      );

      let disciplinaCodigo = dto.codigo;
      let disciplinaNome = dto.disciplinaNome ?? "—";

      if (dto.disciplinaId) {
        try {
          const disc = await fetchDisciplina(dto.disciplinaId);
          disciplinaCodigo = disc.codigo;
          disciplinaNome = disc.nome;
        } catch {
          // Mantém valores retornados pelo DTO da turma
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
    } catch {
      return {
        id: turmaId,
        nomeTurma: `Turma #${turmaId}`,
        semestreAno: "—",
        disciplinaId: 0,
        disciplinaCodigo: "—",
        disciplinaNome: "—",
        rotulo: `Turma #${turmaId}`,
      };
    }
  })();

  turmaResumoCache.set(turmaId, promise);
  return promise;
}

// ---------------------------------------------------------------------------
// Mapeamento de tipo de material (Backend Enum → Frontend TipoMaterial)
// Backend: PDF_LIVRO | PDF_APOSTILA | VIDEO_AULA
// Frontend: "PDF" | "VIDEO"
// ---------------------------------------------------------------------------
function mapTipoMaterial(tipo: string | undefined): TipoMaterial {
  if (tipo === "VIDEO_AULA" || tipo === "VIDEO") {
    return "VIDEO";
  }
  return "PDF";
}

// ---------------------------------------------------------------------------
// Mapeador ConteudoResponseDTO → Conteudo
// ---------------------------------------------------------------------------
async function toConteudo(dto: ConteudoResponseDTO): Promise<Conteudo> {
  const turma = await fetchTurmaResumo(dto.turmaId);

  return {
    id: String(dto.id),
    titulo: dto.titulo,
    descricao: dto.descricao ?? "",
    tipo: mapTipoMaterial(dto.tipoMaterial),
    tamanhoBytes: dto.tamanhoBytes ?? 0,
    criadoEm: dto.criadoEm,
    status: dto.statusPublicacao === "PUBLICADO" ? "PUBLICADO" : "RASCUNHO",
    turma,
    professor: {
      id: dto.professorId ?? 0,
      nome: dto.professorNome || "Docente",
    },
  };
}

// ---------------------------------------------------------------------------
// Implementação do MaterialService HTTP Real
// ---------------------------------------------------------------------------
export const httpMaterialService: MaterialService = {
  /**
   * Lista conteúdos visíveis ao usuário autenticado.
   * O Row Level Security (RLS) do PostgreSQL filtra automaticamente:
   *   - ADMIN: visualiza todos os materiais de todas as turmas.
   *   - PROFESSOR (DOCENTE): visualiza materiais das turmas onde leciona.
   *   - ALUNO: visualiza materiais das turmas onde está matriculado.
   *
   * O parâmetro turmaId é enviado na requisição quando especificado.
   * Filtros adicionais de busca textual, tipo e disciplina são
   * aplicados sobre a lista retornada. O status de publicação vem do backend.
   */
  async list(_sessao: Sessao, filtros?: MaterialFiltros): Promise<Conteudo[]> {
    const params = filtros?.turmaId ? { turmaId: filtros.turmaId } : undefined;

    const dtos = await httpClient.get<ConteudoResponseDTO[]>(
      API_ENDPOINTS.CONTEUDOS.BASE,
      { params },
    );

    if (!dtos || dtos.length === 0) {
      return [];
    }

    const conteudos = await Promise.all(dtos.map(toConteudo));

    let result = conteudos;

    if (filtros) {
      if (filtros.busca) {
        const q = normalize(filtros.busca);
        result = result.filter((c) =>
          normalize(
            `${c.titulo} ${c.descricao} ${c.turma.rotulo} ${c.professor.nome}`,
          ).includes(q),
        );
      }
      if (filtros.tipo) {
        result = result.filter((c) => c.tipo === filtros.tipo);
      }
      if (filtros.status) {
        result = result.filter((c) => c.status === filtros.status);
      }
      if (filtros.disciplinaId) {
        result = result.filter((c) => c.turma.disciplinaId === filtros.disciplinaId);
      }
    }

    return result;
  },

  /**
   * Busca um conteúdo específico por seu identificador.
   * O backend valida se o usuário possui permissão de acesso via RLS:
   * se o usuário não pertencer à turma do conteúdo, o backend retorna 404.
   */
  async get(_sessao: Sessao, id: string): Promise<Conteudo> {
    if (!id || id.trim() === "") {
      throw new ApiError("VALIDATION", "O ID do material é obrigatório.", 400);
    }

    const dto = await httpClient.get<ConteudoResponseDTO>(
      API_ENDPOINTS.CONTEUDOS.BY_ID(id),
    );

    return toConteudo(dto);
  },

  /** Cadastro com upload real (multipart), com progresso e cancelamento. */
  async create(sessao: Sessao, input: MaterialInput, opts?: MutationOpts): Promise<Conteudo> {
    if (!input.arquivo) {
      throw new ApiError("VALIDATION", "Selecione o arquivo do material.", 400);
    }
    const form = new FormData();
    form.append("turmaId", String(input.turmaId));
    form.append("titulo", input.titulo.trim());
    form.append("descricao", input.descricao.trim());
    form.append("tipoMaterial", toBackendTipo(input.tipo));
    form.append("statusPublicacao", input.status);
    if (sessao.usuario.perfil === "ADMIN") {
      form.append("professorId", String(await resolverProfessorDaTurma(input.turmaId)));
    }
    form.append("arquivo", input.arquivo, input.arquivo.name);

    const dto = await httpClient.upload<ConteudoResponseDTO>(API_ENDPOINTS.CONTEUDOS.BASE, form, {
      method: "POST",
      onProgress: opts?.onProgress,
      signal: opts?.signal,
    });
    return toConteudo(dto);
  },

  /** Edição de título/descrição e, opcionalmente, substituição do arquivo. */
  async update(
    _sessao: Sessao,
    id: string,
    input: Partial<MaterialInput>,
    opts?: MutationOpts,
  ): Promise<Conteudo> {
    if (!id || id.trim() === "") {
      throw new ApiError("VALIDATION", "O ID do material é obrigatório.", 400);
    }
    const form = new FormData();
    form.append("titulo", (input.titulo ?? "").trim());
    form.append("descricao", (input.descricao ?? "").trim());
    if (input.arquivo) {
      form.append("arquivo", input.arquivo, input.arquivo.name);
    }

    const dto = await httpClient.upload<ConteudoResponseDTO>(API_ENDPOINTS.CONTEUDOS.BY_ID(id), form, {
      method: "PUT",
      onProgress: opts?.onProgress,
      signal: opts?.signal,
    });
    return toConteudo(dto);
  },

  /** Publica (PUBLICADO) ou despublica (RASCUNHO). */
  async setStatus(_sessao: Sessao, id: string, status: StatusPublicacao): Promise<Conteudo> {
    const dto = await httpClient.patch<ConteudoResponseDTO>(API_ENDPOINTS.CONTEUDOS.STATUS(id), {
      statusPublicacao: status,
    });
    return toConteudo(dto);
  },

  /** Exclusão lógica no backend. */
  async remove(_sessao: Sessao, id: string): Promise<void> {
    await httpClient.delete<void>(API_ENDPOINTS.CONTEUDOS.BY_ID(id));
  },
};

type MutationOpts = { onProgress?: (p: UploadProgress) => void; signal?: AbortSignal };

function toBackendTipo(tipo: TipoMaterial): BackendTipoMaterial {
  return tipo === "VIDEO" ? "VIDEO_AULA" : "PDF_APOSTILA";
}

/** ADMIN cadastra em nome de um docente: usa o primeiro DOCENTE ativo da turma. */
async function resolverProfessorDaTurma(turmaId: number): Promise<number> {
  const participantes = await httpClient.get<TurmaUsuarioResponseDTO[]>(
    API_ENDPOINTS.TURMAS.PARTICIPANTES(turmaId),
  );
  const docente = participantes.find((p) => p.papel === "DOCENTE" && p.status === "ATIVA");
  if (!docente) {
    throw new ApiError("VALIDATION", "A turma não possui docente ativo para ser o autor do material.", 400);
  }
  return docente.usuarioId;
}
