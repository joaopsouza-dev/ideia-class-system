import { DEMO_PASSWORD } from "@/mocks/seed";
import type {
  Conteudo,
  ConteudoRegistro,
  DemoSourceKey,
  LogAcesso,
  Sessao,
  TurmaDetalhe,
  TurmaResumo,
  Usuario,
  UsuarioAdmin,
  ViewerSource,
} from "@/types";
import { normalize } from "@/utils/format";
import type { Services } from "../contracts";
import { ApiError } from "../errors";
import { db, delay } from "./db";

const SESSION_KEY = "ideiaspace.demo.session";

/* ----------------------------- helpers de visão ---------------------------- */

function turmaResumo(turmaId: number): TurmaResumo {
  const t = db.turmas.find((x) => x.id === turmaId);
  if (!t) throw new ApiError("NOT_FOUND", "Turma não encontrada.", 404);
  const d = db.disciplinas.find((x) => x.id === t.disciplinaId)!;
  return {
    id: t.id,
    nomeTurma: t.nomeTurma,
    semestreAno: t.semestreAno,
    disciplinaId: d.id,
    disciplinaCodigo: d.codigo,
    disciplinaNome: d.nome,
    rotulo: `${d.nome} · ${t.nomeTurma}`,
  };
}

function toConteudo(r: ConteudoRegistro): Conteudo {
  const prof = db.usuarios.find((u) => u.id === r.professorId);
  return {
    id: r.uuidPublico,
    titulo: r.titulo,
    descricao: r.descricao,
    tipo: r.tipoMaterial,
    tamanhoBytes: r.tamanhoBytes,
    criadoEm: r.criadoEm,
    status: r.status,
    paginas: r.paginas,
    duracaoSegundos: r.duracaoSegundos,
    turma: turmaResumo(r.turmaId),
    professor: { id: r.professorId, nome: prof?.nomeCompleto ?? "—" },
  };
}

const turmaIdsDe = (usuarioId: number, papel?: "DOCENTE" | "ALUNO") =>
  db.turmaUsuarios
    .filter((tu) => tu.usuarioId === usuarioId && (!papel || tu.papelNaTurma === papel))
    .map((tu) => tu.turmaId);

/** Equivalente às políticas RLS: quais conteúdos o usuário PODE ver. */
function visivel(sessao: Sessao, r: ConteudoRegistro): boolean {
  if (r.removidoEm) return false;
  const u = sessao.usuario;
  if (u.perfil === "ADMIN") return true;
  if (u.perfil === "PROFESSOR") return turmaIdsDe(u.id, "DOCENTE").includes(r.turmaId);
  return r.status === "PUBLICADO" && turmaIdsDe(u.id, "ALUNO").includes(r.turmaId);
}

function podeGerenciar(sessao: Sessao, turmaId: number): boolean {
  const u = sessao.usuario;
  if (u.perfil === "ADMIN") return true;
  return u.perfil === "PROFESSOR" && turmaIdsDe(u.id, "DOCENTE").includes(turmaId);
}

function exigirPerfil(sessao: Sessao, ...perfis: Usuario["perfil"][]) {
  if (!perfis.includes(sessao.usuario.perfil))
    throw new ApiError("FORBIDDEN", "Você não tem permissão para esta ação.", 403);
}

function pushLog(
  sessao: Sessao,
  tipoAcao: LogAcesso["tipoAcao"],
  conteudoId?: string,
  detalhe?: string,
) {
  const c = conteudoId ? db.conteudos.find((x) => x.uuidPublico === conteudoId) : undefined;
  db.logs.push({
    id: db.nextLogId++,
    usuarioId: sessao.usuario.id,
    usuarioNome: sessao.usuario.nomeCompleto,
    conteudoId: c?.uuidPublico ?? null,
    conteudoTitulo: c?.titulo ?? null,
    tipoAcao,
    ipOrigem: "127.0.0.1 (demo)",
    dispositivo: navigator.userAgent.includes("Mobile") ? "Navegador móvel" : "Navegador desktop",
    dataHora: new Date().toISOString(),
    detalhe,
  });
}

const sessionId = () =>
  Array.from(crypto.getRandomValues(new Uint8Array(4)), (b) => b.toString(16).padStart(2, "0"))
    .join("")
    .toUpperCase();

function simulateUpload(
  total: number,
  onProgress?: (p: { loaded: number; total: number }) => void,
  signal?: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    let loaded = 0;
    const step = Math.max(total / 12, 1);
    const timer = setInterval(() => {
      if (signal?.aborted) {
        clearInterval(timer);
        reject(new ApiError("ABORTED", "Envio cancelado.", 499));
        return;
      }
      loaded = Math.min(total, loaded + step);
      onProgress?.({ loaded, total });
      if (loaded >= total) {
        clearInterval(timer);
        resolve();
      }
    }, 120);
  });
}

/* --------------------------------- serviços -------------------------------- */

export const mockServices: Services = {
  auth: {
    async login(email, senha) {
      await delay(600);
      const user = db.usuarios.find((u) => u.email.toLowerCase() === email.trim().toLowerCase());
      // Mesma resposta para e-mail inexistente e senha errada (não revela quais contas existem).
      if (!user || senha !== DEMO_PASSWORD)
        throw new ApiError("UNAUTHORIZED", "E-mail ou senha incorretos.", 401);
      if (!user.statusAtivo)
        throw new ApiError(
          "ACCOUNT_DISABLED",
          "Esta conta está inativa. Procure a administração da Ideia Space.",
          401,
        );
      const sessao: Sessao = {
        usuario: structuredClone(user),
        sessionId: sessionId(),
        iniciadaEm: new Date().toISOString(),
        modo: "DEMO",
      };
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(sessao));
      pushLog(sessao, "LOGIN");
      return sessao;
    },
    async register(input) {
      await delay(700);
      if (db.usuarios.some((u) => u.email.toLowerCase() === input.email.toLowerCase()))
        throw new ApiError("CONFLICT", "Já existe uma conta com este e-mail.", 409);
      const id = db.nextUserId++;
      db.usuarios.push({
        id,
        uuidPublico: crypto.randomUUID(),
        nomeCompleto: input.nomeCompleto.trim(),
        email: input.email.trim(),
        perfil: "ALUNO", // o perfil nunca é escolhido pelo próprio usuário
        statusAtivo: true,
        criadoEm: new Date().toISOString(),
      });
      // O CPF não é guardado: no backend ele é criptografado (pgcrypto) + hash para unicidade.
    },
    async requestPasswordReset() {
      await delay(600);
    },
    async logout(sessao) {
      await delay(150);
      pushLog(sessao, "LOGOUT");
      sessionStorage.removeItem(SESSION_KEY);
    },
    async restore() {
      try {
        const raw = sessionStorage.getItem(SESSION_KEY);
        if (!raw) return null;
        const sessao = JSON.parse(raw) as Sessao;
        const atual = db.usuarios.find((u) => u.id === sessao.usuario.id);
        return atual?.statusAtivo ? { ...sessao, usuario: structuredClone(atual) } : null;
      } catch {
        return null;
      }
    },
  },

  academic: {
    async listTurmas(sessao) {
      await delay(250);
      const u = sessao.usuario;
      const ids =
        u.perfil === "ADMIN" ? db.turmas.map((t) => t.id) : turmaIdsDe(u.id);
      return ids.map((id) => detalheTurma(sessao, id));
    },
    async getTurma(sessao, turmaId) {
      await delay(200);
      const u = sessao.usuario;
      if (u.perfil !== "ADMIN" && !turmaIdsDe(u.id).includes(turmaId))
        throw new ApiError("NOT_FOUND", "Turma não encontrada.", 404); // como o RLS: "não existe"
      return detalheTurma(sessao, turmaId);
    },
    async listTurmasGerenciaveis(sessao) {
      await delay(150);
      const u = sessao.usuario;
      const ids = u.perfil === "ADMIN" ? db.turmas.map((t) => t.id) : turmaIdsDe(u.id, "DOCENTE");
      return ids.map(turmaResumo);
    },
    async listUsuarios(sessao) {
      exigirPerfil(sessao, "ADMIN");
      await delay(300);
      return db.usuarios.map<UsuarioAdmin>((u) => ({
        ...u,
        turmas: db.turmaUsuarios
          .filter((tu) => tu.usuarioId === u.id)
          .map((tu) => ({ id: tu.turmaId, rotulo: turmaResumo(tu.turmaId).rotulo, papel: tu.papelNaTurma })),
      }));
    },
    async setUsuarioAtivo(sessao, usuarioId, ativo) {
      exigirPerfil(sessao, "ADMIN");
      await delay(300);
      if (usuarioId === sessao.usuario.id)
        throw new ApiError("VALIDATION", "Você não pode desativar a própria conta.", 422);
      const u = db.usuarios.find((x) => x.id === usuarioId);
      if (!u) throw new ApiError("NOT_FOUND", "Usuário não encontrado.", 404);
      u.statusAtivo = ativo;
    },
  },

  materials: {
    async list(sessao, f = {}) {
      await delay(300);
      const q = f.busca ? normalize(f.busca) : "";
      return db.conteudos
        .filter((r) => visivel(sessao, r))
        .map(toConteudo)
        .filter(
          (c) =>
            (!q || normalize(`${c.titulo} ${c.descricao} ${c.turma.rotulo}`).includes(q)) &&
            (!f.tipo || c.tipo === f.tipo) &&
            (!f.status || c.status === f.status) &&
            (!f.turmaId || c.turma.id === f.turmaId) &&
            (!f.disciplinaId || c.turma.disciplinaId === f.disciplinaId),
        );
    },
    async get(sessao, id) {
      await delay(200);
      const r = db.conteudos.find((x) => x.uuidPublico === id);
      if (!r || !visivel(sessao, r)) throw new ApiError("NOT_FOUND", "Material não encontrado.", 404);
      return toConteudo(r);
    },
    async create(sessao, input, opts) {
      exigirPerfil(sessao, "PROFESSOR", "ADMIN");
      if (!podeGerenciar(sessao, input.turmaId))
        throw new ApiError("FORBIDDEN", "Você não leciona nesta turma.", 403);
      if (!input.arquivo) throw new ApiError("VALIDATION", "Selecione um arquivo.", 422);
      await simulateUpload(input.arquivo.size, opts?.onProgress, opts?.signal);
      const id = crypto.randomUUID();
      db.uploads.set(id, URL.createObjectURL(input.arquivo));
      const registro: ConteudoRegistro = {
        uuidPublico: id,
        turmaId: input.turmaId,
        professorId: sessao.usuario.id,
        titulo: input.titulo.trim(),
        descricao: input.descricao.trim(),
        tipoMaterial: input.tipo,
        tamanhoBytes: input.arquivo.size,
        criadoEm: new Date().toISOString(),
        removidoEm: null,
        status: input.status,
      };
      db.conteudos.unshift(registro);
      pushLog(sessao, "UPLOAD", id);
      if (input.status === "PUBLICADO") pushLog(sessao, "PUBLICACAO", id);
      return toConteudo(registro);
    },
    async update(sessao, id, input, opts) {
      const r = db.conteudos.find((x) => x.uuidPublico === id);
      if (!r || !visivel(sessao, r)) throw new ApiError("NOT_FOUND", "Material não encontrado.", 404);
      if (!podeGerenciar(sessao, r.turmaId))
        throw new ApiError("FORBIDDEN", "Você não pode editar este material.", 403);
      if (input.turmaId && !podeGerenciar(sessao, input.turmaId))
        throw new ApiError("FORBIDDEN", "Você não leciona na turma de destino.", 403);
      if (input.arquivo) {
        await simulateUpload(input.arquivo.size, opts?.onProgress, opts?.signal);
        const old = db.uploads.get(id);
        if (old) URL.revokeObjectURL(old);
        db.uploads.set(id, URL.createObjectURL(input.arquivo));
        r.tamanhoBytes = input.arquivo.size;
        delete r.fonteDemo;
      } else await delay(400);
      if (input.titulo !== undefined) r.titulo = input.titulo.trim();
      if (input.descricao !== undefined) r.descricao = input.descricao.trim();
      if (input.turmaId !== undefined) r.turmaId = input.turmaId;
      if (input.status !== undefined && input.status !== r.status) {
        r.status = input.status;
        pushLog(sessao, input.status === "PUBLICADO" ? "PUBLICACAO" : "DESPUBLICACAO", id);
      }
      pushLog(sessao, "EDICAO", id);
      return toConteudo(r);
    },
    async setStatus(sessao, id, status) {
      await delay(300);
      const r = db.conteudos.find((x) => x.uuidPublico === id);
      if (!r || !visivel(sessao, r)) throw new ApiError("NOT_FOUND", "Material não encontrado.", 404);
      if (!podeGerenciar(sessao, r.turmaId))
        throw new ApiError("FORBIDDEN", "Você não pode alterar este material.", 403);
      r.status = status;
      pushLog(sessao, status === "PUBLICADO" ? "PUBLICACAO" : "DESPUBLICACAO", id);
      return toConteudo(r);
    },
    async remove(sessao, id) {
      await delay(400);
      const r = db.conteudos.find((x) => x.uuidPublico === id);
      if (!r || !visivel(sessao, r)) throw new ApiError("NOT_FOUND", "Material não encontrado.", 404);
      if (!podeGerenciar(sessao, r.turmaId))
        throw new ApiError("FORBIDDEN", "Você não pode excluir este material.", 403);
      r.removidoEm = new Date().toISOString(); // soft delete, como no banco
      pushLog(sessao, "EXCLUSAO", id);
    },
  },

  content: {
    async getViewerSource(sessao, id) {
      await delay(500);
      const r = db.conteudos.find((x) => x.uuidPublico === id);
      if (!r || !visivel(sessao, r)) throw new ApiError("NOT_FOUND", "Material não encontrado.", 404);
      pushLog(sessao, r.tipoMaterial === "PDF" ? "ABRIU_PDF" : "ABRIU_VIDEO", id);
      const upload = db.uploads.get(id);
      if (upload)
        return { url: upload, origem: "UPLOAD_LOCAL", paginas: r.paginas, duracaoSegundos: r.duracaoSegundos };
      const key: DemoSourceKey | undefined = r.fonteDemo;
      if (!key) throw new ApiError("NOT_FOUND", "Arquivo indisponível nesta sessão de demonstração.", 404);
      const src: ViewerSource =
        key === "video-orbitas"
          ? {
              url: "/demo/demo.mp4",
              posterUrl: "/demo/demo-poster.jpg",
              origem: "DEMO",
              duracaoSegundos: r.duracaoSegundos,
            }
          : { url: `/demo/${key}.pdf`, origem: "DEMO", paginas: r.paginas };
      return src;
    },
  },

  accessLog: {
    async record(sessao, evento) {
      pushLog(sessao, evento.tipoAcao, evento.conteudoId, evento.detalhe);
    },
    async list(sessao) {
      exigirPerfil(sessao, "ADMIN", "PROFESSOR");
      await delay(300);
      const u = sessao.usuario;
      if (u.perfil === "ADMIN") return [...db.logs].reverse();
      // professor: eventos dos conteúdos das turmas que leciona
      const meus = new Set(
        db.conteudos.filter((c) => podeGerenciar(sessao, c.turmaId)).map((c) => c.uuidPublico),
      );
      return db.logs.filter((l) => l.conteudoId && meus.has(l.conteudoId)).reverse();
    },
  },
};

function detalheTurma(sessao: Sessao, id: number): TurmaDetalhe {
  const base = turmaResumo(id);
  const membros = db.turmaUsuarios.filter((tu) => tu.turmaId === id);
  const docentes = membros
    .filter((m) => m.papelNaTurma === "DOCENTE")
    .map((m) => ({ id: m.usuarioId, nome: db.usuarios.find((u) => u.id === m.usuarioId)!.nomeCompleto }));
  return {
    ...base,
    docentes,
    totalAlunos: membros.filter((m) => m.papelNaTurma === "ALUNO").length,
    totalMateriais: db.conteudos.filter((c) => c.turmaId === id && visivel(sessao, c)).length,
  };
}
