/**
 * DADOS DE DEMONSTRAÇÃO — não representam dados reais.
 *
 * Usuários, disciplinas e turmas seguem o seed do banco (02_seed_dev.sql, Sprint 2 v2.2)
 * para facilitar a troca por dados reais. Disciplinas 3 e 4 e os conteúdos foram
 * acrescentados apenas para a demonstração (acervo temático da Ideia Space).
 */
import type {
  ConteudoRegistro,
  Disciplina,
  LogAcesso,
  Turma,
  TurmaUsuario,
  Usuario,
} from "@/types";

export const DEMO_PASSWORD = "Senha@123";

const DAY = 86_400_000;
const daysAgo = (n: number, hour = 10) => {
  const d = new Date(Date.now() - n * DAY);
  d.setHours(hour, 15, 0, 0);
  return d.toISOString();
};

export const seedUsuarios: Usuario[] = [
  {
    id: 1,
    uuidPublico: "a1b2c3d4-0001-4000-8000-000000000001",
    nomeCompleto: "Administrador Ideia Space",
    email: "admin@ideiaspace.dev",
    perfil: "ADMIN",
    statusAtivo: true,
    criadoEm: daysAgo(120),
  },
  {
    id: 2,
    uuidPublico: "a1b2c3d4-0002-4000-8000-000000000002",
    nomeCompleto: "Victor Emanoel Silva",
    email: "victor.emanoel@ideiaspace.dev",
    perfil: "PROFESSOR",
    statusAtivo: true,
    criadoEm: daysAgo(110),
  },
  {
    id: 3,
    uuidPublico: "a1b2c3d4-0003-4000-8000-000000000003",
    nomeCompleto: "André Luiz Santos",
    email: "andre.luiz@ideiaspace.dev",
    perfil: "PROFESSOR",
    statusAtivo: true,
    criadoEm: daysAgo(108),
  },
  {
    id: 4,
    uuidPublico: "a1b2c3d4-0004-4000-8000-000000000004",
    nomeCompleto: "Maria Clara Ferreira",
    email: "maria.clara@ideiaspace.dev",
    perfil: "ALUNO",
    statusAtivo: true,
    criadoEm: daysAgo(90),
  },
  {
    id: 5,
    uuidPublico: "a1b2c3d4-0005-4000-8000-000000000005",
    nomeCompleto: "João Pedro Alves",
    email: "joao.pedro@ideiaspace.dev",
    perfil: "ALUNO",
    statusAtivo: false,
    criadoEm: daysAgo(88),
  },
  {
    id: 6,
    uuidPublico: "a1b2c3d4-0006-4000-8000-000000000006",
    nomeCompleto: "Lucas Mendes Rocha",
    email: "lucas.mendes@ideiaspace.dev",
    perfil: "ALUNO",
    statusAtivo: true,
    criadoEm: daysAgo(60),
  },
];

export const seedDisciplinas: Disciplina[] = [
  {
    id: 1,
    codigo: "PWEB",
    nome: "Programação Web",
    descricao: "Fundamentos de interfaces web com React e TypeScript.",
  },
  {
    id: 2,
    codigo: "BDAV",
    nome: "Banco de Dados Avançado",
    descricao: "Modelagem, segurança e operação de bancos relacionais.",
  },
  {
    id: 3,
    codigo: "MORB",
    nome: "Mecânica Orbital",
    descricao: "Movimento de corpos celestes e satélites artificiais.",
  },
  {
    id: 4,
    codigo: "SATE",
    nome: "Satélites e Telemetria",
    descricao: "Subsistemas de satélites, estação terrena e análise de dados.",
  },
];

export const seedTurmas: Turma[] = [
  { id: 1, disciplinaId: 1, nomeTurma: "Turma A", semestreAno: "2026/2" },
  { id: 2, disciplinaId: 2, nomeTurma: "Turma B", semestreAno: "2026/2" },
  { id: 3, disciplinaId: 3, nomeTurma: "Turma A", semestreAno: "2026/2" },
  { id: 4, disciplinaId: 4, nomeTurma: "Turma B", semestreAno: "2026/2" },
];

export const seedTurmaUsuarios: TurmaUsuario[] = [
  { id: 1, turmaId: 1, usuarioId: 2, papelNaTurma: "DOCENTE" },
  { id: 2, turmaId: 3, usuarioId: 2, papelNaTurma: "DOCENTE" },
  { id: 3, turmaId: 2, usuarioId: 3, papelNaTurma: "DOCENTE" },
  { id: 4, turmaId: 4, usuarioId: 3, papelNaTurma: "DOCENTE" },
  { id: 5, turmaId: 1, usuarioId: 4, papelNaTurma: "ALUNO" },
  { id: 6, turmaId: 3, usuarioId: 4, papelNaTurma: "ALUNO" },
  { id: 7, turmaId: 4, usuarioId: 4, papelNaTurma: "ALUNO" },
  { id: 8, turmaId: 2, usuarioId: 5, papelNaTurma: "ALUNO" },
  { id: 9, turmaId: 1, usuarioId: 6, papelNaTurma: "ALUNO" },
  { id: 10, turmaId: 2, usuarioId: 6, papelNaTurma: "ALUNO" },
  { id: 11, turmaId: 3, usuarioId: 6, papelNaTurma: "ALUNO" },
];

const uid = (n: number) => `c0n7e000-0000-4000-8000-${String(n).padStart(12, "0")}`;

export const seedConteudos: ConteudoRegistro[] = [
  {
    uuidPublico: uid(1),
    turmaId: 3,
    professorId: 2,
    titulo: "Apostila: Leis de Kepler",
    descricao:
      "As três leis do movimento planetário, com exemplos aplicados a satélites e exercícios de fixação.",
    tipoMaterial: "PDF",
    tamanhoBytes: 7_706,
    criadoEm: daysAgo(2),
    removidoEm: null,
    status: "PUBLICADO",
    paginas: 5,
    fonteDemo: "kepler",
  },
  {
    uuidPublico: uid(2),
    turmaId: 3,
    professorId: 2,
    titulo: "Videoaula: Órbitas no Sistema Solar",
    descricao:
      "Visão geral de órbitas elípticas e como a velocidade orbital varia ao longo do percurso. (Vídeo de demonstração.)",
    tipoMaterial: "VIDEO",
    tamanhoBytes: 235_728,
    criadoEm: daysAgo(5),
    removidoEm: null,
    status: "PUBLICADO",
    duracaoSegundos: 22,
    fonteDemo: "video-orbitas",
  },
  {
    uuidPublico: uid(3),
    turmaId: 3,
    professorId: 2,
    titulo: "Lista de exercícios 01 — Órbitas",
    descricao: "Rascunho da primeira lista de exercícios. Ainda não visível para os alunos.",
    tipoMaterial: "PDF",
    tamanhoBytes: 7_706,
    criadoEm: daysAgo(1),
    removidoEm: null,
    status: "RASCUNHO",
    paginas: 5,
    fonteDemo: "kepler",
  },
  {
    uuidPublico: uid(4),
    turmaId: 4,
    professorId: 3,
    titulo: "Apostila: Telemetria de satélites",
    descricao:
      "Estrutura de quadros de telemetria, estação terrena e boas práticas de análise de dados.",
    tipoMaterial: "PDF",
    tamanhoBytes: 5_888,
    criadoEm: daysAgo(12),
    removidoEm: null,
    status: "PUBLICADO",
    paginas: 4,
    fonteDemo: "telemetria",
  },
  {
    uuidPublico: uid(5),
    turmaId: 4,
    professorId: 3,
    titulo: "Videoaula: Introdução à estação terrena",
    descricao:
      "Como a estação terrena acompanha passagens e recebe sinais. (Vídeo de demonstração.)",
    tipoMaterial: "VIDEO",
    tamanhoBytes: 235_728,
    criadoEm: daysAgo(20),
    removidoEm: null,
    status: "PUBLICADO",
    duracaoSegundos: 22,
    fonteDemo: "video-orbitas",
  },
  {
    uuidPublico: uid(6),
    turmaId: 2,
    professorId: 3,
    titulo: "Apostila: Segurança por linha (RLS)",
    descricao: "Row-Level Security no PostgreSQL: variáveis de sessão, políticas e auditoria.",
    tipoMaterial: "PDF",
    tamanhoBytes: 4_917,
    criadoEm: daysAgo(8),
    removidoEm: null,
    status: "PUBLICADO",
    paginas: 3,
    fonteDemo: "rls",
  },
  {
    uuidPublico: uid(7),
    turmaId: 2,
    professorId: 3,
    titulo: "Revisão de modelagem lógica",
    descricao: "Material em preparação sobre normalização e relacionamentos.",
    tipoMaterial: "PDF",
    tamanhoBytes: 4_917,
    criadoEm: daysAgo(3),
    removidoEm: null,
    status: "RASCUNHO",
    paginas: 3,
    fonteDemo: "rls",
  },
  {
    uuidPublico: uid(8),
    turmaId: 1,
    professorId: 2,
    titulo: "Guia: Componentes e estado em React",
    descricao: "Componentes, props, estado local e efeitos, com foco em organização de código.",
    tipoMaterial: "PDF",
    tamanhoBytes: 5_100,
    criadoEm: daysAgo(15),
    removidoEm: null,
    status: "PUBLICADO",
    paginas: 3,
    fonteDemo: "react",
  },
  {
    uuidPublico: uid(9),
    turmaId: 1,
    professorId: 2,
    titulo: "Conteúdo removido (soft delete)",
    descricao: "Não deve aparecer em nenhuma listagem.",
    tipoMaterial: "PDF",
    tamanhoBytes: 1_000,
    criadoEm: daysAgo(40),
    removidoEm: daysAgo(30),
    status: "PUBLICADO",
    paginas: 3,
    fonteDemo: "react",
  },
];

export function buildSeedLogs(): LogAcesso[] {
  const rows: Omit<LogAcesso, "id">[] = [];
  const dev = ["Chrome · Windows", "Firefox · Linux", "Safari · iOS", "Chrome · Android"];
  const add = (
    userId: number,
    name: string,
    tipo: LogAcesso["tipoAcao"],
    when: string,
    cid: string | null,
    title: string | null,
    i: number,
    detalhe?: string,
  ) =>
    rows.push({
      usuarioId: userId,
      usuarioNome: name,
      conteudoId: cid,
      conteudoTitulo: title,
      tipoAcao: tipo,
      ipOrigem: `189.40.${10 + i}.${20 + i * 3}`,
      dispositivo: dev[i % dev.length],
      dataHora: when,
      detalhe,
    });
  add(4, "Maria Clara Ferreira", "LOGIN", daysAgo(0, 8), null, null, 0);
  add(4, "Maria Clara Ferreira", "ABRIU_PDF", daysAgo(0, 8), uid(1), "Apostila: Leis de Kepler", 0);
  add(6, "Lucas Mendes Rocha", "LOGIN", daysAgo(1, 19), null, null, 1);
  add(6, "Lucas Mendes Rocha", "ABRIU_VIDEO", daysAgo(1, 19), uid(2), "Videoaula: Órbitas no Sistema Solar", 1);
  add(6, "Lucas Mendes Rocha", "TENTATIVA_CAPTURA", daysAgo(1, 19), uid(2), "Videoaula: Órbitas no Sistema Solar", 1, "Tecla PrintScreen");
  add(4, "Maria Clara Ferreira", "ABRIU_PDF", daysAgo(2, 21), uid(4), "Apostila: Telemetria de satélites", 2);
  add(4, "Maria Clara Ferreira", "TENTATIVA_IMPRESSAO", daysAgo(2, 21), uid(4), "Apostila: Telemetria de satélites", 2, "Atalho Ctrl+P");
  add(2, "Victor Emanoel Silva", "UPLOAD", daysAgo(2, 9), uid(1), "Apostila: Leis de Kepler", 3);
  add(3, "André Luiz Santos", "UPLOAD", daysAgo(12, 14), uid(4), "Apostila: Telemetria de satélites", 0);
  add(6, "Lucas Mendes Rocha", "ABRIU_PDF", daysAgo(3, 20), uid(6), "Apostila: Segurança por linha (RLS)", 1);
  add(6, "Lucas Mendes Rocha", "PERDA_DE_FOCO", daysAgo(3, 20), uid(6), "Apostila: Segurança por linha (RLS)", 1, "Janela perdeu o foco");
  add(5, "João Pedro Alves", "LOGIN", daysAgo(30, 10), null, null, 2);
  add(2, "Victor Emanoel Silva", "PUBLICACAO", daysAgo(5, 11), uid(2), "Videoaula: Órbitas no Sistema Solar", 3);
  add(4, "Maria Clara Ferreira", "ABRIU_VIDEO", daysAgo(4, 17), uid(5), "Videoaula: Introdução à estação terrena", 2);
  add(4, "Maria Clara Ferreira", "TENTATIVA_COPIA", daysAgo(4, 17), uid(5), "Videoaula: Introdução à estação terrena", 2, "Evento de cópia bloqueado");
  return rows
    .sort((a, b) => a.dataHora.localeCompare(b.dataHora))
    .map((r, i) => ({ ...r, id: i + 1 }));
}
