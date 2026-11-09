/**
 * Tipos de domínio do frontend.
 *
 * Origem: "Modelo Lógico – Projeto Ideia Space" e "Resumo da Sprint 2 (v2.2)".
 * Os nomes seguem o modelo lógico em camelCase. Campos que NÃO devem chegar ao
 * navegador (hash_senha, cpf, caminho_storage) foram propositalmente omitidos.
 */

export type Perfil = "ADMIN" | "PROFESSOR" | "ALUNO";
export type PapelNaTurma = "DOCENTE" | "ALUNO";
export type TipoMaterial = "PDF" | "VIDEO";
export type StatusPublicacao = "PUBLICADO" | "RASCUNHO";

export interface Usuario {
  id: number;
  /** uuid_publico do banco — é o identificador que deve trafegar na API */
  uuidPublico: string;
  nomeCompleto: string;
  email: string;
  perfil: Perfil;
  statusAtivo: boolean;
  criadoEm: string;
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

/** Conteúdo como persistido (mock). Nunca exponha `caminhoStorage` ao frontend real. */
export interface ConteudoRegistro {
  uuidPublico: string;
  turmaId: number;
  professorId: number;
  titulo: string;
  descricao: string;
  tipoMaterial: TipoMaterial;
  tamanhoBytes: number;
  criadoEm: string;
  /** soft delete (removido_em) */
  removidoEm: string | null;
  /** PENDENTE no banco: não existe coluna de publicação — ver docs/INCONSISTENCIAS */
  status: StatusPublicacao;
  paginas?: number;
  duracaoSegundos?: number;
  /** Só no mock: chave do arquivo de demonstração */
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
  /** rótulo pronto: "Mecânica Orbital · Turma A" */
  rotulo: string;
}

/** Conteúdo como a UI consome (visão montada pelo backend/adapter). */
export interface Conteudo {
  id: string;
  titulo: string;
  descricao: string;
  tipo: TipoMaterial;
  tamanhoBytes: number;
  criadoEm: string;
  status: StatusPublicacao;
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
  /** identificador de sessão exibido na marca d'água */
  sessionId: string;
  iniciadaEm: string;
  /** modo de autenticação — "DEMO" até existir backend */
  modo: "DEMO" | "API";
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

export interface RegistroAcessoInput {
  tipoAcao: TipoAcao;
  conteudoId?: string;
  detalhe?: string;
}

export interface MaterialFiltros {
  busca?: string;
  tipo?: TipoMaterial | "";
  turmaId?: number | null;
  disciplinaId?: number | null;
  status?: StatusPublicacao | "";
}

export interface MaterialInput {
  titulo: string;
  descricao: string;
  tipo: TipoMaterial;
  turmaId: number;
  status: StatusPublicacao;
  arquivo?: File | null;
}

/** Fonte de visualização entregue pelo backend (URL temporária / assinada). */
export interface ViewerSource {
  url: string;
  origem: "DEMO" | "UPLOAD_LOCAL" | "API";
  /** ISO — quando a URL assinada expira (backend) */
  expiraEm?: string;
  paginas?: number;
  duracaoSegundos?: number;
  posterUrl?: string;
}

export interface UsuarioAdmin extends Usuario {
  turmas: { id: number; rotulo: string; papel: PapelNaTurma }[];
}

export interface RegistroUsuarioInput {
  nomeCompleto: string;
  email: string;
  cpf: string;
  senha: string;
}
