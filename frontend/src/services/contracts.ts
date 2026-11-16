/**
 * Contratos da camada de serviços.
 *
 * A UI depende APENAS destas interfaces. Hoje elas são implementadas por adapters
 * de demonstração (src/services/mock). Quando o backend existir, basta criar
 * adapters HTTP que as implementem e trocar em services/index.ts.
 *
 * A autorização REAL (turma/disciplina/perfil) é do backend (RLS). Os adapters mock
 * reproduzem o comportamento esperado apenas para permitir a demonstração.
 */
import type {
  Conteudo,
  LogAcesso,
  MaterialFiltros,
  MaterialInput,
  AtualizarUsuarioInput,
  AlunoDisponivel,
  ParticipanteTurma,
  RegistroAcessoInput,
  RegistroUsuarioInput,
  Sessao,
  StatusPublicacao,
  TurmaDetalhe,
  TurmaResumo,
  UsuarioAdmin,
  ViewerSource,
} from "@/types";

export interface AuthService {
  login(email: string, senha: string): Promise<Sessao>;
  register(input: RegistroUsuarioInput): Promise<void>;
  requestPasswordReset(email: string): Promise<void>;
  logout(sessao: Sessao): Promise<void>;
  /** Recupera a sessão persistida (no mock, sessionStorage; na API, cookie/refresh token). */
  restore(): Promise<Sessao | null>;
  /** Altera a senha do próprio usuário; exige a senha atual. */
  alterarSenha(sessao: Sessao, senhaAtual: string, novaSenha: string): Promise<void>;
}

export interface AcademicService {
  /** Turmas visíveis ao usuário (docente: as que leciona; aluno: as que cursa; admin: todas). */
  listTurmas(sessao: Sessao): Promise<TurmaDetalhe[]>;
  getTurma(sessao: Sessao, turmaId: number): Promise<TurmaDetalhe>;
  /** Turmas onde o usuário pode publicar conteúdo. */
  listTurmasGerenciaveis(sessao: Sessao): Promise<TurmaResumo[]>;
  listUsuarios(sessao: Sessao): Promise<UsuarioAdmin[]>;
  setUsuarioAtivo(sessao: Sessao, usuarioId: number, ativo: boolean): Promise<void>;
  atualizarUsuario(sessao: Sessao, usuarioId: number, dados: AtualizarUsuarioInput): Promise<UsuarioAdmin>;
  /** Participantes (alunos e docentes) de uma turma. */
  listParticipantes(sessao: Sessao, turmaId: number): Promise<ParticipanteTurma[]>;
  /** Alunos ativos disponíveis para matrícula (ADMIN ou DOCENTE). */
  listAlunosDisponiveis(sessao: Sessao): Promise<AlunoDisponivel[]>;
  /** Matricula um aluno (DISCENTE) na turma. */
  vincularAluno(sessao: Sessao, turmaId: number, usuarioId: number): Promise<ParticipanteTurma>;
  /** Remove (soft-delete) o vínculo de um participante. */
  removerParticipante(sessao: Sessao, turmaId: number, vinculoId: number): Promise<void>;
}

export interface UploadProgress {
  loaded: number;
  total: number;
}

export interface MaterialService {
  list(sessao: Sessao, filtros?: MaterialFiltros): Promise<Conteudo[]>;
  get(sessao: Sessao, id: string): Promise<Conteudo>;
  create(
    sessao: Sessao,
    input: MaterialInput,
    opts?: { onProgress?: (p: UploadProgress) => void; signal?: AbortSignal },
  ): Promise<Conteudo>;
  update(
    sessao: Sessao,
    id: string,
    input: Partial<MaterialInput>,
    opts?: { onProgress?: (p: UploadProgress) => void; signal?: AbortSignal },
  ): Promise<Conteudo>;
  setStatus(sessao: Sessao, id: string, status: StatusPublicacao): Promise<Conteudo>;
  remove(sessao: Sessao, id: string): Promise<void>;
}

export interface ContentAccessService {
  /**
   * Obtém a fonte de visualização (URL temporária/assinada) para PDF ou vídeo.
   * O frontend nunca conhece o caminho real do arquivo no storage.
   */
  getViewerSource(sessao: Sessao, conteudoId: string): Promise<ViewerSource>;
}

export interface AccessLogService {
  /** Registro de ação. O backend deve ignorar o ip/dispositivo enviados e usar os próprios. */
  record(sessao: Sessao, evento: RegistroAcessoInput): Promise<void>;
  list(sessao: Sessao): Promise<LogAcesso[]>;
}

export interface Services {
  auth: AuthService;
  academic: AcademicService;
  materials: MaterialService;
  content: ContentAccessService;
  accessLog: AccessLogService;
}
