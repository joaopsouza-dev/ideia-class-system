/**
 * Teste automatizado de validação ponta a ponta da Etapa 2:
 * Integração da Biblioteca de Materiais (Frontend HTTP Adapter <-> Backend Spring Boot).
 */

import { httpAuthService } from "../services/http/auth";
import { httpMaterialService } from "../services/http/materials";
import { httpContentAccessService } from "../services/http/content";
import { httpAccessLogService } from "../services/http/accessLog";
import { httpClient } from "../services/api";
import { ApiError } from "../services/errors";
import type { Sessao } from "../types";

const ADMIN_EMAIL = "admin.integ@ideiaspace.com";
const ADMIN_PASS = "admin_strong_password_123";

const DOCENTE_EMAIL = "docente.integ@ideiaspace.com";
const DOCENTE_PASS = "docente_strong_password_123";

const ALUNO_EMAIL = "aluno.integ@ideiaspace.com";
const ALUNO_PASS = "aluno_strong_password_123";

async function assert(condition: boolean, msg: string) {
  if (!condition) {
    throw new Error(`FALHA NA ASSERTIVA: ${msg}`);
  }
  console.log(`  ✓ ${msg}`);
}

async function run() {
  console.log("===============================================================");
  console.log("INICIANDO TESTES DE VALIDAÇÃO: ETAPAS 2 E 3 — BIBLIOTECA E GERENCIAMENTO DE MATERIAIS");
  console.log("===============================================================\n");

  // -------------------------------------------------------------------------
  // 1. Teste de Envio do JWT e Autenticação
  // -------------------------------------------------------------------------
  console.log("1. Autenticação e Obtenção de Sessão com JWT:");
  const adminSessao: Sessao = await httpAuthService.login(ADMIN_EMAIL, ADMIN_PASS);
  assert(!!adminSessao.token, "Admin autenticado com sucesso e JWT recebido");
  assert(adminSessao.usuario.perfil === "ADMIN", "Perfil do usuário admin é ADMIN");

  const docenteSessao: Sessao = await httpAuthService.login(DOCENTE_EMAIL, DOCENTE_PASS);
  assert(!!docenteSessao.token, "Docente autenticado com sucesso e JWT recebido");
  assert(docenteSessao.usuario.perfil === "PROFESSOR", "Perfil mapeado para PROFESSOR");

  const alunoSessao: Sessao = await httpAuthService.login(ALUNO_EMAIL, ALUNO_PASS);
  assert(!!alunoSessao.token, "Aluno autenticado com sucesso e JWT recebido");
  assert(alunoSessao.usuario.perfil === "ALUNO", "Perfil do usuário aluno é ALUNO");

  // -------------------------------------------------------------------------
  // 2. Teste de Listagem Real de Materiais (Admin)
  // -------------------------------------------------------------------------
  console.log("\n2. Listagem Real de Materiais (Admin):");
  httpClient.setToken(adminSessao.token!);
  const materialsAdmin = await httpMaterialService.list(adminSessao);
  assert(Array.isArray(materialsAdmin), "Retorno da listagem é um Array");
  assert(materialsAdmin.length > 0, `Materiais encontrados no backend: ${materialsAdmin.length}`);

  const sample = materialsAdmin[0];
  console.log("  Amostra de material mapeado:", {
    id: sample.id,
    titulo: sample.titulo,
    tipo: sample.tipo,
    status: sample.status,
    turmaRotulo: sample.turma?.rotulo,
    professorNome: sample.professor?.nome,
  });

  assert(typeof sample.id === "string", "ID do material mapeado como string");
  assert(sample.status === "PUBLICADO" || sample.status === "RASCUNHO", "Status de publicação vem do backend");
  assert(sample.tipo === "PDF" || sample.tipo === "VIDEO", "Tipo de material válido (PDF ou VIDEO)");
  assert(!!sample.turma && !!sample.turma.rotulo, "TurmaResumo preenchido com rotulo");
  assert(!!sample.professor && !!sample.professor.nome, "Dados do professor preenchidos");

  // -------------------------------------------------------------------------
  // 3. Teste de Busca por ID (Admin)
  // -------------------------------------------------------------------------
  console.log("\n3. Busca por ID (Admin):");
  const materialPorId = await httpMaterialService.get(adminSessao, sample.id);
  assert(materialPorId.id === sample.id, `Material recuperado pelo ID ${sample.id}`);
  assert(materialPorId.titulo === sample.titulo, "Título confere com o item da lista");
  assert(materialPorId.turma.id === sample.turma.id, "Turma do material confere");

  // -------------------------------------------------------------------------
  // 4. Teste de Filtros e Pesquisas
  // -------------------------------------------------------------------------
  console.log("\n4. Filtros e Pesquisas:");
  // Filtro por turmaId
  const turmaId = sample.turma.id;
  const porTurma = await httpMaterialService.list(adminSessao, { turmaId });
  assert(porTurma.every((m) => m.turma.id === turmaId), `Filtro por turmaId=${turmaId} retornou apenas da turma`);

  // Filtro por tipo
  const porTipo = await httpMaterialService.list(adminSessao, { tipo: sample.tipo });
  assert(porTipo.every((m) => m.tipo === sample.tipo), `Filtro por tipo=${sample.tipo} retornou apenas desse tipo`);

  // Filtro textual (busca)
  const termo = sample.titulo.slice(0, 8);
  const porBusca = await httpMaterialService.list(adminSessao, { busca: termo });
  assert(porBusca.length > 0, `Busca textual por '${termo}' encontrou resultados`);
  assert(
    porBusca.some((m) => m.titulo.toLowerCase().includes(termo.toLowerCase())),
    "Resultado da busca contém o termo pesquisado",
  );

  // Filtro sem correspondência (lista vazia)
  const vazia = await httpMaterialService.list(adminSessao, { busca: "XYZ_TERMO_INEXISTENTE_999" });
  assert(Array.isArray(vazia) && vazia.length === 0, "Filtro sem correspondência retorna lista vazia []");

  // -------------------------------------------------------------------------
  // 5. Teste de Permissões por Perfil (Docente e Aluno)
  // -------------------------------------------------------------------------
  console.log("\n5. Permissões por Perfil:");
  // Docente
  httpClient.setToken(docenteSessao.token!);
  const materialsDocente = await httpMaterialService.list(docenteSessao);
  assert(Array.isArray(materialsDocente), "Docente listou materiais com sucesso");
  console.log(`  Docente visualiza ${materialsDocente.length} materiais das suas turmas`);

  // Aluno
  httpClient.setToken(alunoSessao.token!);
  const materialsAluno = await httpMaterialService.list(alunoSessao);
  assert(Array.isArray(materialsAluno), "Aluno listou materiais com sucesso");
  console.log(`  Aluno visualiza ${materialsAluno.length} materiais das turmas matriculadas`);

  if (materialsAluno.length > 0) {
    const alunoMat = await httpMaterialService.get(alunoSessao, materialsAluno[0].id);
    assert(!!alunoMat, `Aluno acessou com sucesso o material ID ${alunoMat.id} da sua turma`);
  }

  // -------------------------------------------------------------------------
  // 6. Teste de Tratamento de Erros
  // -------------------------------------------------------------------------
  console.log("\n6. Tratamento de Erros:");
  // ID inexistente
  try {
    await httpMaterialService.get(adminSessao, "999999");
    assert(false, "Busca por ID inexistente deveria lançar erro");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 404, "ID inexistente retornou ApiError com status 404");
  }

  // ID vazio
  try {
    await httpMaterialService.get(adminSessao, "");
    assert(false, "Busca com ID vazio deveria lançar erro de validação");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 400, "ID vazio retornou ApiError com status 400 (VALIDATION)");
  }

  // Requisição sem token (401)
  httpClient.setToken(null);
  try {
    await httpMaterialService.list(adminSessao);
    assert(false, "Requisição sem token deveria lançar 401");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 401, "Chamada sem token retornou ApiError com status 401 (UNAUTHORIZED)");
  }

  // -------------------------------------------------------------------------
  // 7. Ciclo real de gerenciamento (Etapa 3): cadastro, edição, publicação, exclusão
  // -------------------------------------------------------------------------
  console.log("\n7. Gerenciamento real (docente): upload, edição, publicação e exclusão:");
  httpClient.setToken(docenteSessao.token!);
  const turmaDocente = materialsDocente[0]?.turma.id ?? sample.turma.id;
  const pdfBytes = new TextEncoder().encode("%PDF-1.4\nteste e2e\n%%EOF\n");
  const arquivo = new File([pdfBytes], "e2e.pdf", { type: "application/pdf" });

  const criado = await httpMaterialService.create(docenteSessao, {
    titulo: "Material E2E " + Date.now(),
    descricao: "criado pelo teste",
    tipo: "PDF",
    turmaId: turmaDocente,
    status: "RASCUNHO",
    arquivo,
  });
  assert(criado.status === "RASCUNHO" && criado.tamanhoBytes === pdfBytes.length, "Cadastro real de PDF como rascunho");

  httpClient.setToken(alunoSessao.token!);
  try {
    await httpMaterialService.get(alunoSessao, criado.id);
    assert(false, "Aluno não deveria ver rascunho");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 404, "Aluno recebe 404 para rascunho");
  }

  httpClient.setToken(docenteSessao.token!);
  const publicado = await httpMaterialService.setStatus(docenteSessao, criado.id, "PUBLICADO");
  assert(publicado.status === "PUBLICADO", "Publicação funciona");
  const editado = await httpMaterialService.update(docenteSessao, criado.id, { titulo: "Título editado E2E", descricao: "x" });
  assert(editado.titulo === "Título editado E2E", "Edição de título funciona");
  const novoArquivo = new File([new TextEncoder().encode("%PDF-1.4\nsubstituto maior\n%%EOF\n")], "n.pdf");
  const substituido = await httpMaterialService.update(docenteSessao, criado.id, { titulo: "Título editado E2E", descricao: "x", arquivo: novoArquivo });
  assert(substituido.tamanhoBytes !== criado.tamanhoBytes, "Substituição de arquivo atualiza o tamanho");

  // -------------------------------------------------------------------------
  // 8. Visualização protegida (URL temporária) e auditoria
  // -------------------------------------------------------------------------
  console.log("\n8. Visualização protegida e auditoria:");
  httpClient.setToken(alunoSessao.token!);
  const fonte = await httpContentAccessService.getViewerSource(alunoSessao, criado.id);
  assert(fonte.origem === "API" && fonte.url.length > 0 && !!fonte.expiraEm, "Aluno recebe URL temporária com expiração");
  assert(new Date(fonte.expiraEm!).getTime() > Date.now(), "A URL temporária ainda não expirou");
  const baixado = await fetch(fonte.url); // sem Authorization: a autorização está na própria URL
  assert(baixado.status === 200, "URL temporária entrega o arquivo (200)");
  assert((await baixado.text()).startsWith("%PDF-"), "O conteúdo entregue é o PDF enviado");

  await httpAccessLogService.record(alunoSessao, { tipoAcao: "TENTATIVA_DOWNLOAD", conteudoId: criado.id, detalhe: "teste e2e" });
  await httpAccessLogService.record(alunoSessao, { tipoAcao: "PERDA_DE_FOCO", conteudoId: criado.id }); // sem equivalente no banco: ignorado

  httpClient.setToken(docenteSessao.token!);
  await httpMaterialService.setStatus(docenteSessao, criado.id, "RASCUNHO");
  httpClient.setToken(alunoSessao.token!);
  try {
    await httpContentAccessService.getViewerSource(alunoSessao, criado.id);
    assert(false, "Aluno não deveria abrir rascunho");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 404, "Aluno recebe 404 ao tentar abrir rascunho");
  }
  httpClient.setToken(docenteSessao.token!);
  await httpMaterialService.setStatus(docenteSessao, criado.id, "PUBLICADO");

  httpClient.setToken(null);
  try {
    await httpContentAccessService.getViewerSource(alunoSessao, criado.id);
    assert(false, "Sem token deveria falhar");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 401, "Visualização sem token retorna 401");
  }

  httpClient.setToken(adminSessao.token!);
  const logs = await httpAccessLogService.list(adminSessao);
  const doMaterial = logs.filter((l) => l.conteudoId === criado.id);
  assert(doMaterial.some((l) => l.tipoAcao === "ABRIU_PDF" && l.usuarioId === alunoSessao.usuario.id), "Auditoria: ABRIU_PDF do aluno registrado");
  assert(doMaterial.some((l) => l.tipoAcao === "TENTATIVA_DOWNLOAD"), "Auditoria: tentativa de download registrada");
  assert(doMaterial.some((l) => l.tipoAcao === "TENTATIVA_ACESSO_NEGADO"), "Auditoria: acesso negado ao rascunho registrado");
  assert(doMaterial.every((l) => l.ipOrigem !== "—"), "Auditoria: IP determinado pelo servidor em todos os eventos");

  try {
    await httpMaterialService.create(docenteSessao, { titulo: "Inválido", descricao: "", tipo: "PDF", turmaId: turmaDocente, status: "RASCUNHO", arquivo: new File(["texto"], "x.pdf", { type: "application/pdf" }) });
    assert(false, "Arquivo inválido deveria ser rejeitado");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 415, "Arquivo inválido rejeitado pelo backend (415)");
  }

  httpClient.setToken(alunoSessao.token!);
  try {
    await httpMaterialService.remove(alunoSessao, criado.id);
    assert(false, "Aluno não deveria excluir");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 403, "Aluno não gerencia materiais (403)");
  }

  httpClient.setToken(docenteSessao.token!);
  await httpMaterialService.remove(docenteSessao, criado.id);
  try {
    await httpMaterialService.get(docenteSessao, criado.id);
    assert(false, "Material excluído deveria sumir");
  } catch (err: any) {
    assert(err instanceof ApiError && err.status === 404, "Exclusão lógica: material some (404)");
  }

  console.log("\n===============================================================");
  console.log("TODOS OS TESTES DE INTEGRAÇÃO PASSARAM COM SUCESSO!");
  console.log("===============================================================");
}

run().catch((e) => {
  console.error("ERRO DURANTE OS TESTES:", e);
  process.exit(1);
});
