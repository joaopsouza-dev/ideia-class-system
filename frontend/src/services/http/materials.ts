/**
 * Adapter HTTP real para MaterialService (Etapa 2 — Integração da Biblioteca).
 *
 * Implementa o contrato MaterialService usando exclusivamente os endpoints
 * confirmados no backend Spring Boot:
 *   - GET /api/v1/conteudos            → lista conteúdos (filtrados pelo RLS do banco)
 *   - GET /api/v1/conteudos?turmaId=X  → lista conteúdos de uma turma específica
 *   - GET /api/v1/conteudos/{id}       → busca conteúdo por ID
 *   - GET /api/v1/turmas/{id}          → resolve metadados da turma e disciplina
 *
 * Funcionalidades não incluídas nesta primeira entrega (lançam NOT_IMPLEMENTED):
 *   - create    → cadastro de materiais com upload real
 *   - update    → edição de materiais
 *   - setStatus → publicação/despublicação
 *   - remove    → exclusão de materiais
 */

import type { MaterialService } from "../contracts";
import { ApiError } from "../errors";
import { httpClient, API_ENDPOINTS } from "../api";
import type {
  ConteudoResponseDTO,
  TurmaResponseDTO,
  DisciplinaResponseDTO,
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
    status: undefined,
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
   * aplicados sobre a lista retornada. O backend ainda não persiste status de publicação,
   * portanto não há filtro por situação.
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

  /**
   * NÃO IMPLEMENTADO nesta primeira entrega de integração.
   * Cadastro de novos conteúdos requer upload/armazenamento real (Etapa final).
   */
  async create(_sessao: Sessao, _input: MaterialInput): Promise<Conteudo> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Cadastro de materiais não disponível nesta etapa de integração.",
      501,
    );
  },

  /**
   * NÃO IMPLEMENTADO nesta primeira entrega de integração.
   */
  async update(
    _sessao: Sessao,
    _id: string,
    _input: Partial<MaterialInput>,
  ): Promise<Conteudo> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Edição de materiais não disponível nesta etapa de integração.",
      501,
    );
  },

  /**
   * NÃO IMPLEMENTADO nesta primeira entrega de integração.
   */
  async setStatus(
    _sessao: Sessao,
    _id: string,
    _status: StatusPublicacao,
  ): Promise<Conteudo> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Alteração de situação de material não disponível nesta etapa de integração.",
      501,
    );
  },

  /**
   * NÃO IMPLEMENTADO nesta primeira entrega de integração.
   */
  async remove(_sessao: Sessao, _id: string): Promise<void> {
    throw new ApiError(
      "NOT_IMPLEMENTED",
      "Exclusão de materiais não disponível nesta etapa de integração.",
      501,
    );
  },
};
