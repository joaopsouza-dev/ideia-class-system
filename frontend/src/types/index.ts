// Types definitions for the frontend.

export type Perfil = "ADMIN" | "PROFESSOR" | "ALUNO";
export type PapelNaTurma = "DOCENTE" | "ALUNO";
export type TipoMaterial = "PDF" | "VIDEO";
export type StatusPublicacao = "PUBLICADO" | "RASCUNHO"; // retained for type safety, optional in data.

/** Material filters used in UI */
export interface MaterialFiltros {
  busca?: string;
  tipo?: TipoMaterial | "";
  turmaId?: number | null;
  disciplinaId?: number | null;
  status?: StatusPublicacao | ""; // usado apenas pelo mock; a UI e o backend não expõem status.
}

export interface MaterialInput {
  titulo: string;
  descricao: string;
  tipo: TipoMaterial;
  turmaId: number;
  status: StatusPublicacao;
  arquivo?: File | null;
}

export interface Usuario {
  id: number;
  nomeCompleto: string;
  email: string;
  perfil: Perfil;
  uuidPublico?: string;
  statusAtivo?: boolean;
  criadoEm?: string;
}

export interface Disciplina {
  id: number;
  codigo: string;
  nome: string;
  descricao: string;
}

export interface Turma {
  id: number;
  disciplinaId: number;
  nomeTurma: string;
  semestreAno: string;
}

export interface TurmaUsuario {
  id: number;
  turmaId: number;
  usuarioId: number;
  papelNaTurma: PapelNaTurma;
}

/** Conteúdo persistido (mock). */
export interface ConteudoRegistro {
  uuidPublico: string;
  turmaId: number;
  professorId: number;
  titulo: string;
  descricao: string;
  tipoMaterial: TipoMaterial;
  tamanhoBytes: number;
  criadoEm: string;
  removidoEm: string | null;
  status: StatusPublicacao; // só existe no mock; o backend não fornece.
  paginas?: number;
  duracaoSegundos?: number;
  fonteDemo?: DemoSourceKey;
}

export type DemoSourceKey = "kepler" | "telemetria" | "rls" | "react" | "video-orbitas";

export interface TurmaResumo {
  id: number;
  nomeTurma: string;
  semestreAno: string;
  disciplinaId: number;
  disciplinaCodigo: string;
  disciplinaNome: string;
  rotulo: string;
}

/** UI-facing content */
export interface Conteudo {
  id: string;
  titulo: string;
  descricao: string;
  tipo: TipoMaterial;
  tamanhoBytes: number;
  criadoEm: string;
  status?: StatusPublicacao;
  paginas?: number;
  duracaoSegundos?: number;
  turma: TurmaResumo;
  professor: { id: number; nome: string };
}

export interface TurmaDetalhe extends TurmaResumo {
  docentes: { id: number; nome: string }[];
  totalAlunos: number;
  totalMateriais: number;
}

export interface Sessao {
  usuario: Usuario;
  sessionId: string;
  iniciadaEm: string;
  modo: "DEMO" | "API";
  token?: string;
}

export type TipoAcao =
  | "LOGIN"
  | "LOGOUT"
  | "ABRIU_PDF"
  | "ABRIU_VIDEO"
  | "TENTATIVA_CAPTURA"
  | "TENTATIVA_IMPRESSAO"
  | "TENTATIVA_DOWNLOAD"
  | "TENTATIVA_COPIA"
  | "PERDA_DE_FOCO"
  | "MARCA_DAGUA_ALTERADA"
  | "TENTATIVA_ACESSO_NEGADO"
  | "GEROU_URL_TEMPORARIA"
  | "UPLOAD"
  | "EDICAO"
  | "PUBLICACAO"
  | "DESPUBLICACAO"
  | "EXCLUSAO";

export interface LogAcesso {
  id: number;
  usuarioId: number;
  usuarioNome: string;
  conteudoId: string | null;
  conteudoTitulo: string | null;
  tipoAcao: TipoAcao;
  ipOrigem: string;
  dispositivo: string;
  dataHora: string;
  detalhe?: string;
}

/** Participante de uma turma (vínculo DISCENTE ou DOCENTE, com status da matrícula). */
export interface ParticipanteTurma {
  vinculoId: number;
  usuarioId: number;
  nome: string;
  email: string;
  papel: PapelNaTurma;
  status: string;
}

/** Aluno ativo que pode ser matriculado. */
export interface AlunoDisponivel {
  id: number;
  nomeCompleto: string;
  email: string;
}

export interface RegistroAcessoInput {
  tipoAcao: TipoAcao;
  conteudoId?: string;
  detalhe?: string;
}

export interface UsuarioAdmin extends Usuario {
  statusAtivo: boolean;
  criadoEm: string;
  turmas: { id: number; rotulo: string; papel: PapelNaTurma }[];
}

/** Campos editáveis pelo administrador (ou nome/e-mail pelo próprio usuário). */
export interface AtualizarUsuarioInput {
  nomeCompleto?: string;
  email?: string;
  perfil?: Perfil;
}

export interface RegistroUsuarioInput {
  nomeCompleto: string;
  email: string;
  cpf: string;
  senha: string;
}

export interface ViewerSource {
  url: string;
  origem: "DEMO" | "UPLOAD_LOCAL" | "API";
  expiraEm?: string;
  paginas?: number;
  duracaoSegundos?: number;
  posterUrl?: string;
}
