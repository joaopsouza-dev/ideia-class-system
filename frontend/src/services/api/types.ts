/**
 * Tipagens dos DTOs e entidades do backend real Ideia Space.
 * Mapeamento estrito dos DTOs em Java (com.catolica.ideiaspace.dto.*).
 */

export type PerfilUsuario = "ADMIN" | "DOCENTE" | "ALUNO";
export type PapelTurma = "DOCENTE" | "ALUNO" | "MONITOR";
export type StatusMatricula = "ATIVA" | "TRANCADA" | "CONCLUIDA" | "CANCELADA";
export type StatusTurma = "PLANEJADA" | "ATIVA" | "CONCLUIDA" | "CANCELADA";
export type TipoMaterial = "PDF" | "VIDEO";

/** Estrutura padrão de erro retornada pelo GlobalExceptionHandler e SecurityConfig */
export interface BackendErrorResponse {
  timestamp: string;
  status: number;
  error: string;
}

// ---------------------------------------------------------------------------
// Auth DTOs (/api/v1/auth)
// ---------------------------------------------------------------------------

export interface LoginRequestDTO {
  email: string;
  senha: string;
}

export interface UsuarioDTO {
  id: number;
  nomeCompleto: string;
  email: string;
  perfil: PerfilUsuario;
}

export interface LoginResponseDTO {
  token: string;
  tipo: string;
  usuario: UsuarioDTO;
}

export interface ProvisionRequestDTO {
  nomeCompleto: string;
  email: string;
  senha: string;
  perfil: PerfilUsuario;
}

export interface ProvisionResponseDTO {
  id: number;
  mensagem: string;
}

// ---------------------------------------------------------------------------
// Disciplina DTOs (/api/v1/disciplinas)
// ---------------------------------------------------------------------------

export interface DisciplinaRequestDTO {
  codigo: string;
  nome: string;
  descricao?: string | null;
}

export interface DisciplinaUpdateDTO {
  codigo: string;
  nome: string;
  descricao?: string | null;
}

export interface DisciplinaResponseDTO {
  id: number;
  codigo: string;
  nome: string;
  descricao: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

// ---------------------------------------------------------------------------
// Turma DTOs (/api/v1/turmas)
// ---------------------------------------------------------------------------

export interface TurmaRequestDTO {
  disciplinaId: number;
  codigo: string;
  nome: string;
  ano: number;
  semestre: number;
  status?: StatusTurma;
}

export interface TurmaUpdateDTO {
  disciplinaId: number;
  codigo: string;
  nome: string;
  ano: number;
  semestre: number;
  status?: StatusTurma;
}

export interface TurmaResponseDTO {
  id: number;
  disciplinaId: number;
  disciplinaNome: string | null;
  codigo: string;
  nome: string;
  ano: number;
  semestre: number;
  status: StatusTurma;
  criadoEm: string;
  atualizadoEm: string;
}

export interface TurmaUsuarioRequestDTO {
  usuarioId: number;
  papel: PapelTurma;
  inicioEm?: string | null;
  fimEm?: string | null;
}

export interface TurmaUsuarioResponseDTO {
  id: number;
  turmaId: number;
  usuarioId: number;
  usuarioNome: string;
  usuarioEmail: string;
  papel: PapelTurma;
  status: StatusMatricula;
  inicioEm: string | null;
  fimEm: string | null;
  criadoEm: string;
}

// ---------------------------------------------------------------------------
// Conteúdo DTOs (/api/v1/conteudos)
// ---------------------------------------------------------------------------

export interface ConteudoRequestDTO {
  turmaId: number;
  professorId?: number | null;
  titulo: string;
  descricao?: string | null;
  tipoMaterial: TipoMaterial;
  storagePath: string;
  mimeType: string;
  tamanhoBytes: number;
  sha256?: string | null;
}

export interface ConteudoUpdateDTO {
  titulo: string;
  descricao?: string | null;
  tipoMaterial: TipoMaterial;
  storagePath: string;
  mimeType: string;
  tamanhoBytes: number;
  sha256?: string | null;
}

export interface ConteudoResponseDTO {
  id: number;
  turmaId: number;
  professorId: number;
  professorNome: string | null;
  titulo: string;
  descricao: string | null;
  tipoMaterial: TipoMaterial;
  mimeType: string;
  tamanhoBytes: number;
  sha256: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

// ---------------------------------------------------------------------------
// Opções de Requisição do Cliente HTTP
// ---------------------------------------------------------------------------

export interface RequestOptions {
  headers?: Record<string, string>;
  params?: Record<string, string | number | boolean | undefined | null>;
  /** Define se o cabeçalho Authorization: Bearer <token> deve ser incluído automaticamente (padrão: true) */
  auth?: boolean;
  signal?: AbortSignal;
}

export interface UploadProgress {
  loaded: number;
  total: number;
}

export interface UploadOptions extends RequestOptions {
  onProgress?: (progress: UploadProgress) => void;
}
