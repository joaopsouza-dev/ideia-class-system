/**
 * Tipagens dos DTOs e entidades do backend real Ideia Space.
 * Mapeamento estrito dos DTOs em Java (com.catolica.ideiaspace.dto.*).
 */

export type PerfilUsuario = "ADMIN" | "DOCENTE" | "ALUNO";
export type PapelTurma = "DOCENTE" | "DISCENTE";
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

export interface AlunoDisponivelResponseDTO {
  id: number;
  nomeCompleto: string;
  email: string;
}

export interface AuditoriaResponseDTO {
  id: number;
  criadoEm: string;
  usuarioId: number;
  usuarioNome: string;
  perfilUsuario: PerfilUsuario;
  conteudoId: number | null;
  conteudoTitulo: string | null;
  acao: string;
  ipOrigem: string | null;
  userAgent: string | null;
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

export type BackendTipoMaterial = "PDF_LIVRO" | "PDF_APOSTILA" | "VIDEO_AULA";

// ---------------------------------------------------------------------------
// Conteúdo DTOs (/api/v1/conteudos)
// ---------------------------------------------------------------------------

export type BackendStatusPublicacao = "RASCUNHO" | "PUBLICADO";

/**
 * Cadastro/edição são multipart/form-data (campos + parte "arquivo"). O cliente NÃO envia
 * caminho, MIME, tamanho nem hash: o backend os determina a partir do arquivo.
 */
export interface ConteudoStatusDTO {
  statusPublicacao: BackendStatusPublicacao;
}

export interface ConteudoResponseDTO {
  id: number;
  turmaId: number;
  professorId: number | null;
  professorNome: string | null;
  titulo: string;
  descricao: string | null;
  tipoMaterial: BackendTipoMaterial | TipoMaterial;
  statusPublicacao: BackendStatusPublicacao;
  mimeType: string;
  tamanhoBytes: number;
  sha256: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

/**
 * Resposta de GET /api/v1/conteudos/{id}/acesso. "url" é temporária (Signed URL V4 do bucket,
 * ou caminho relativo da API no provedor local de desenvolvimento) e nunca deve ser persistida.
 */
export interface AcessoConteudoResponseDTO {
  url: string;
  expiraEm: string;
  tipo: "PDF" | "VIDEO";
  mimeType: string;
}

/** Ações que o navegador pode reportar a POST /api/v1/conteudos/{id}/eventos. */
export type BackendAcaoEventoCliente = "TENTATIVA_DOWNLOAD" | "TENTATIVA_PRINT_DETECTADA";

export interface EventoAcessoRequestDTO {
  acao: BackendAcaoEventoCliente;
  detalhe?: string;
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
  /** Método HTTP do envio multipart (padrão: POST). */
  method?: "POST" | "PUT";
  onProgress?: (progress: UploadProgress) => void;
}

/** Resposta de GET /api/v1/usuarios (sem senha ou hash). */
export interface UsuarioAdminResponseDTO {
  id: number;
  nomeCompleto: string;
  email: string;
  perfil: "ADMIN" | "DOCENTE" | "ALUNO";
  statusConta: "ATIVA" | "INATIVA" | "BLOQUEADA";
  criadoEm: string;
  turmas: { turmaId: number; turmaNome: string; papel: "DISCENTE" | "DOCENTE" }[];
}
