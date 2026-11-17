/**
 * Teste autossuficiente (sem backend nem banco) dos adapters de visualização protegida e de
 * registro de eventos. Sobe um servidor HTTP local que imita o CONTRATO do backend:
 *   GET  /api/v1/conteudos/{id}/acesso   e   POST /api/v1/conteudos/{id}/eventos
 * Valida o que é responsabilidade do frontend: mapeamento da resposta, resolução de URL relativa,
 * ausência de caminho interno, tratamento de 404/401, deduplicação de chamadas simultâneas e
 * filtragem dos eventos que têm equivalente no banco. NÃO substitui o teste com backend real.
 *
 * Execução: npm run test:content
 */
import http from "node:http";
import type { AddressInfo } from "node:net";
import { httpClient } from "../services/api";
import { ApiError } from "../services/errors";
import { httpContentAccessService } from "../services/http/content";
import { httpAccessLogService } from "../services/http/accessLog";
import type { Sessao } from "../types";

function assert(cond: boolean, msg: string): void {
  if (!cond) throw new Error(`FALHA NA ASSERTIVA: ${msg}`);
  console.log(`  ✓ ${msg}`);
}

const sessao = {
  usuario: { id: 7, nome: "Aluno Teste", email: "a@x.com", perfil: "ALUNO" },
  sessionId: "s",
  iniciadaEm: new Date().toISOString(),
  modo: "API",
  token: "jwt-de-teste",
} as unknown as Sessao;

const recebidos: { method: string; url: string; auth?: string; body?: unknown }[] = [];
let chamadasAcesso = 0;

const server = http.createServer((req, res) => {
  let corpo = "";
  req.on("data", (c) => (corpo += c));
  req.on("end", () => {
    recebidos.push({
      method: req.method ?? "",
      url: req.url ?? "",
      auth: req.headers.authorization,
      body: corpo ? JSON.parse(corpo) : undefined,
    });
    const json = (status: number, obj: unknown) => {
      res.writeHead(status, { "Content-Type": "application/json" });
      res.end(JSON.stringify(obj));
    };
    const m = /^\/api\/v1\/conteudos\/(\d+)\/(acesso|eventos)$/.exec(req.url ?? "");
    if (!m) return json(404, { error: "rota" });
    const [, id, acao] = m;
    if (req.headers.authorization !== "Bearer jwt-de-teste") return json(401, { error: "token" });
    if (id === "404") return json(404, { error: "Conteúdo não encontrado com ID: 404" });
    if (acao === "acesso") {
      chamadasAcesso++;
      // 1: URL relativa (provedor local); 2: URL absoluta (Signed URL do bucket)
      const url =
        id === "1"
          ? "/api/v1/storage-local/arquivo?o=materiais%2F2026%2F11%2Fx.pdf&e=1&s=abc"
          : "https://storage.googleapis.com/bucket/materiais/2026/11/x.pdf?X-Goog-Signature=abc";
      return setTimeout(
        () => json(200, { url, expiraEm: "2026-11-16T21:00:00Z", tipo: "PDF", mimeType: "application/pdf" }),
        30,
      );
    }
    res.writeHead(204);
    res.end();
  });
});

async function run() {
  await new Promise<void>((ok) => server.listen(0, "127.0.0.1", ok));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  httpClient.setBaseUrl(base);
  httpClient.setToken("jwt-de-teste");

  console.log("1. getViewerSource:");
  const relativa = await httpContentAccessService.getViewerSource(sessao, "1");
  assert(relativa.url === `${base}/api/v1/storage-local/arquivo?o=materiais%2F2026%2F11%2Fx.pdf&e=1&s=abc`, "URL relativa recebe a base da API");
  assert(relativa.origem === "API" && relativa.expiraEm === "2026-11-16T21:00:00Z", "Origem API e expiração repassadas");
  const absoluta = await httpContentAccessService.getViewerSource(sessao, "2");
  assert(absoluta.url.startsWith("https://storage.googleapis.com/"), "Signed URL absoluta passa inalterada");
  assert(!JSON.stringify(relativa).includes("storagePath") && !("storagePath" in relativa), "ViewerSource não expõe caminho de storage");
  assert(recebidos.every((r) => r.auth === "Bearer jwt-de-teste"), "JWT enviado ao backend");

  console.log("\n2. Deduplicação de chamadas simultâneas (um evento de auditoria por abertura):");
  const antes = chamadasAcesso;
  const [a, b] = await Promise.all([
    httpContentAccessService.getViewerSource(sessao, "1"),
    httpContentAccessService.getViewerSource(sessao, "1"),
  ]);
  assert(chamadasAcesso - antes === 1 && a.url === b.url, "Duas chamadas simultâneas geram uma única requisição");
  await httpContentAccessService.getViewerSource(sessao, "1");
  assert(chamadasAcesso - antes === 2, "Nova abertura depois da anterior gera nova requisição");

  console.log("\n3. Erros:");
  try {
    await httpContentAccessService.getViewerSource(sessao, "404");
    assert(false, "deveria falhar");
  } catch (e) {
    assert(e instanceof ApiError && e.status === 404 && e.code === "NOT_FOUND", "404 vira ApiError NOT_FOUND");
  }
  try {
    await httpContentAccessService.getViewerSource(sessao, " ");
    assert(false, "deveria falhar");
  } catch (e) {
    assert(e instanceof ApiError && e.code === "VALIDATION", "ID vazio é rejeitado antes da requisição");
  }
  httpClient.setToken(null);
  try {
    await httpContentAccessService.getViewerSource(sessao, "1");
    assert(false, "deveria falhar");
  } catch (e) {
    assert(e instanceof ApiError && e.status === 401, "Sem token: 401");
  }
  httpClient.setToken("jwt-de-teste");

  console.log("\n4. record (eventos do navegador):");
  recebidos.length = 0;
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_DOWNLOAD", conteudoId: "1", detalhe: "Menu de contexto" });
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_IMPRESSAO", conteudoId: "1", detalhe: "Atalho de impressão" });
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_CAPTURA", conteudoId: "1", detalhe: "Tecla PrintScreen" });
  assert(recebidos.length === 3 && recebidos.every((r) => r.method === "POST" && r.url === "/api/v1/conteudos/1/eventos"), "Três eventos enviados a POST /conteudos/{id}/eventos");
  const corpos = recebidos.map((r) => r.body as { acao: string; detalhe?: string });
  assert(corpos[0].acao === "TENTATIVA_DOWNLOAD", "Download mapeado para TENTATIVA_DOWNLOAD");
  assert(corpos[1].acao === "TENTATIVA_PRINT_DETECTADA" && corpos[1].detalhe === "Impressão: Atalho de impressão", "Impressão mapeada para TENTATIVA_PRINT_DETECTADA com rótulo");
  assert(corpos[2].acao === "TENTATIVA_PRINT_DETECTADA" && corpos[2].detalhe === "Captura de tela: Tecla PrintScreen", "Captura mapeada para TENTATIVA_PRINT_DETECTADA com rótulo");
  assert(recebidos.every((r) => !JSON.stringify(r.body).match(/ip|userAgent|dispositivo/i)), "Corpo do evento não carrega IP/User-Agent");

  recebidos.length = 0;
  await httpAccessLogService.record(sessao, { tipoAcao: "PERDA_DE_FOCO", conteudoId: "1" });
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_COPIA", conteudoId: "1" });
  await httpAccessLogService.record(sessao, { tipoAcao: "MARCA_DAGUA_ALTERADA", conteudoId: "1" });
  await httpAccessLogService.record(sessao, { tipoAcao: "ABRIU_PDF", conteudoId: "1" }); // só o servidor registra aberturas
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_DOWNLOAD" }); // sem conteúdo: nada a enviar
  assert(recebidos.length === 0, "Eventos sem equivalente no banco (e aberturas) não são enviados");

  const longo = "x".repeat(500);
  await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_DOWNLOAD", conteudoId: "1", detalhe: longo });
  assert(((recebidos[0].body as { detalhe: string }).detalhe).length === 200, "Detalhe limitado a 200 caracteres");

  try {
    await httpAccessLogService.record(sessao, { tipoAcao: "TENTATIVA_DOWNLOAD", conteudoId: "404" });
    assert(false, "deveria falhar");
  } catch (e) {
    assert(e instanceof ApiError && e.status === 404, "Erro do backend é propagado ao chamador");
  }

  console.log("\nTODOS OS TESTES DO ADAPTER DE VISUALIZAÇÃO PASSARAM.");
}

run()
  .catch((e) => {
    console.error("ERRO DURANTE OS TESTES:", e);
    process.exitCode = 1;
  })
  .finally(() => server.close());
