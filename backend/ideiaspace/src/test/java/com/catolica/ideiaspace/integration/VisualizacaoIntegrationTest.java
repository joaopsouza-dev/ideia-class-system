package com.catolica.ideiaspace.integration;

import com.catolica.ideiaspace.service.storage.FileStorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static com.catolica.ideiaspace.integration.IntegracaoSupport.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Visualização protegida + auditoria, com o provedor "local" (sem Firebase real):
 * quem pode abrir, URL temporária (válida, expirada, adulterada), eventos do navegador e o
 * registro real em log_acesso (IP e User-Agent determinados pelo servidor).
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VisualizacaoIntegrationTest {

    private static final String UA = "IdeiaSpaceTest/1.0";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    @Autowired
    private FileStorageService storage;

    private MockMvc mvc;
    private IntegracaoSupport apoio;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilter(springSecurityFilterChain).build();
        apoio = new IntegracaoSupport(mvc);
    }

    private static String docenteToken;
    private static String estranhoToken;
    private static String alunoToken;
    private static Long alunoId;
    private static String alunoForaToken;
    private static Long turmaId;
    private static Long pdfId;
    private static Long videoId;
    private static String adminTokenEst;
    private static final byte[] PDF = IntegracaoSupport.pdf("visualizacao-protegida");
    private static final byte[] MP4 = IntegracaoSupport.mp4();

    private Long cadastrar(String token, MockMultipartFile file, String tipo, String status) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/v1/conteudos").file(file)
                        .header("Authorization", bearer(token))
                        .param("turmaId", String.valueOf(turmaId))
                        .param("titulo", "Visualização " + tipo)
                        .param("tipoMaterial", tipo)
                        .param("statusPublicacao", status))
                .andExpect(status().isCreated()).andReturn();
        return IntegracaoSupport.idDe(r);
    }

    private void definirStatus(Long id, String status) throws Exception {
        mvc.perform(patch("/api/v1/conteudos/{id}/status", id)
                        .header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusPublicacao\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private String acessoUrl(String token, Long id) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/conteudos/{id}/acesso", id)
                        .header("Authorization", bearer(token)).header("User-Agent", UA)
                        .header("X-Forwarded-For", "6.6.6.6"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.url");
    }

    private static String param(String url, String nome) {
        String q = url.substring(url.indexOf('?') + 1);
        for (String par : q.split("&")) {
            if (par.startsWith(nome + "=")) {
                return par.substring(nome.length() + 1);
            }
        }
        return null;
    }

    private List<Object> auditoria(Long conteudoId, String acao) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/auditoria").header("Authorization", bearer(adminTokenEst)))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(),
                "$[?(@.conteudoId==" + conteudoId + " && @.acao=='" + acao + "')]");
    }

    // ------------------------------------------------------------------ preparação

    @Test
    @Order(1)
    @DisplayName("Preparação: turma, docente autor, aluno matriculado, aluno de fora e docente sem vínculo")
    void test01_setup() throws Exception {
        apoio.loginAdmin();
        adminTokenEst = apoio.adminToken;
        IntegracaoSupport.Usuario docente = apoio.provisionar("DOCENTE", "docente.vz");
        IntegracaoSupport.Usuario aluno = apoio.provisionar("ALUNO", "aluno.vz");
        docenteToken = docente.token();
        alunoToken = aluno.token();
        alunoId = aluno.id();
        alunoForaToken = apoio.provisionar("ALUNO", "alunofora.vz").token();
        estranhoToken = apoio.provisionar("DOCENTE", "estranho.vz").token();
        turmaId = apoio.criarTurma("VZ");
        apoio.vincular(turmaId, docente.id(), "DOCENTE");
        apoio.vincular(turmaId, aluno.id(), "DISCENTE");

        pdfId = cadastrar(docenteToken, new MockMultipartFile("arquivo", "a.pdf", "application/pdf", PDF), "PDF_APOSTILA", "RASCUNHO");
        videoId = cadastrar(docenteToken, new MockMultipartFile("arquivo", "v.mp4", "video/mp4", MP4), "VIDEO_AULA", "RASCUNHO");
    }

    // ------------------------------------------------------------------ bloqueios

    @Test
    @Order(2)
    @DisplayName("Rascunho: aluno matriculado recebe 404 (igual a inexistente) e a tentativa é auditada")
    void test02_rascunhoBloqueado() throws Exception {
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", 999999999L).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());

        List<Object> negados = auditoria(pdfId, "TENTATIVA_ACESSO_NEGADO");
        assertFalse(negados.isEmpty(), "tentativa negada registrada pelo servidor");
    }

    @Test
    @Order(3)
    @DisplayName("Sem token: 401. Docente sem vínculo e aluno de fora: 404")
    void test03_semPermissao() throws Exception {
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(estranhoToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(alunoForaToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(4)
    @DisplayName("Autor abre o próprio rascunho: URL temporária com expiração e sem caminho interno no corpo")
    void test04_autorAbreRascunho() throws Exception {
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.url").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("PDF"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.expiraEm").isNotEmpty())
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andExpect(jsonPath("$.caminho").doesNotExist());
    }

    // ------------------------------------------------------------------ aluno autorizado

    @Test
    @Order(5)
    @DisplayName("Publicado: aluno da turma recebe URL temporária (futura) e a URL entrega os bytes do PDF")
    void test05_alunoAbrePdf() throws Exception {
        definirStatus(pdfId, "PUBLICADO");

        MvcResult r = mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId)
                        .header("Authorization", bearer(alunoToken)).header("User-Agent", UA)
                        .header("X-Forwarded-For", "6.6.6.6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PDF"))
                .andReturn();
        String corpo = r.getResponse().getContentAsString();
        String url = JsonPath.read(corpo, "$.url");
        OffsetDateTime expira = OffsetDateTime.parse(JsonPath.read(corpo, "$.expiraEm"));
        assertTrue(expira.toInstant().isAfter(Instant.now()), "expiração no futuro");
        assertTrue(expira.toInstant().isBefore(Instant.now().plusSeconds(3601)), "prazo dentro do teto");

        // A URL não exige Authorization: a autorização está na assinatura.
        MvcResult arquivo = mvc.perform(get(URI.create(url)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertArrayEquals(PDF, arquivo.getResponse().getContentAsByteArray());

        mvc.perform(get(URI.create(url)).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("%PDF".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @Order(6)
    @DisplayName("Vídeo publicado: aluno recebe tipo VIDEO e a URL entrega os bytes do MP4")
    void test06_alunoAbreVideo() throws Exception {
        definirStatus(videoId, "PUBLICADO");
        MvcResult r = mvc.perform(get("/api/v1/conteudos/{id}/acesso", videoId)
                        .header("Authorization", bearer(alunoToken)).header("User-Agent", UA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("VIDEO"))
                .andExpect(jsonPath("$.mimeType").value("video/mp4"))
                .andReturn();
        String url = JsonPath.read(r.getResponse().getContentAsString(), "$.url");
        MvcResult arquivo = mvc.perform(get(URI.create(url))).andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4")).andReturn();
        assertArrayEquals(MP4, arquivo.getResponse().getContentAsByteArray());
    }

    @Test
    @Order(7)
    @DisplayName("Publicado, mas aluno de outra turma e docente sem vínculo continuam com 404")
    void test07_outraTurma() throws Exception {
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(alunoForaToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", videoId).header("Authorization", bearer(estranhoToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(8)
    @DisplayName("Admin abre qualquer material")
    void test08_admin() throws Exception {
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(adminTokenEst)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ URL temporária

    @Test
    @Order(9)
    @DisplayName("URL adulterada ou expirada é recusada (403); sem parâmetros: 400")
    void test09_urlTemporaria() throws Exception {
        String url = acessoUrl(alunoToken, pdfId);
        String caminho = URLDecoder.decode(param(url, "o"), StandardCharsets.UTF_8);

        String assinatura = param(url, "s");
        String adulterada = url.replace("&s=" + assinatura, "&s=" + assinatura.substring(1) + "A");
        mvc.perform(get(URI.create(adulterada))).andExpect(status().isForbidden());

        String estendida = url.replaceFirst("&e=\\d+", "&e=" + (Instant.now().plusSeconds(86400).getEpochSecond()));
        mvc.perform(get(URI.create(estendida))).andExpect(status().isForbidden());

        String expirada = storage.gerarUrlLeitura(caminho, Instant.now().minusSeconds(5)).url();
        mvc.perform(get(URI.create(expirada))).andExpect(status().isForbidden());

        String valida = storage.gerarUrlLeitura(caminho, Instant.now().plusSeconds(60)).url();
        mvc.perform(get(URI.create(valida))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/storage-local/arquivo")).andExpect(status().isBadRequest());
        String traversal = storage.gerarUrlLeitura(caminho, Instant.now().plusSeconds(60)).url()
                .replaceFirst("o=[^&]+", "o=..%2F..%2Fetc%2Fpasswd");
        mvc.perform(get(URI.create(traversal))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ eventos do navegador

    @Test
    @Order(10)
    @DisplayName("Eventos do navegador: download e impressão aceitos (204); aberturas forjadas e ações desconhecidas, 400")
    void test10_eventos() throws Exception {
        mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                        .header("Authorization", bearer(alunoToken)).header("User-Agent", UA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"TENTATIVA_DOWNLOAD\",\"detalhe\":\"Menu de contexto\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                        .header("Authorization", bearer(alunoToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"TENTATIVA_PRINT_DETECTADA\",\"detalhe\":\"Impressão: atalho\"}"))
                .andExpect(status().isNoContent());

        for (String proibida : List.of("ABRIU_PDF", "INICIOU_VIDEO", "TENTATIVA_ACESSO_NEGADO", "GEROU_URL_TEMPORARIA", "INEXISTENTE")) {
            mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                            .header("Authorization", bearer(alunoToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"acao\":\"" + proibida + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                        .header("Authorization", bearer(alunoToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                        .header("Authorization", bearer(alunoForaToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"TENTATIVA_DOWNLOAD\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/conteudos/{id}/eventos", pdfId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"TENTATIVA_DOWNLOAD\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ auditoria

    @Test
    @Order(11)
    @DisplayName("Auditoria: aberturas e tentativas gravadas com usuário do JWT, IP da conexão e User-Agent do servidor")
    void test11_auditoria() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/auditoria").header("Authorization", bearer(adminTokenEst)))
                .andExpect(status().isOk()).andReturn();
        String json = r.getResponse().getContentAsString();

        List<Object> abriuPdf = JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='ABRIU_PDF' && @.usuarioId==" + alunoId + ")]");
        assertFalse(abriuPdf.isEmpty(), "ABRIU_PDF do aluno registrado");
        List<String> ips = JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='ABRIU_PDF' && @.usuarioId==" + alunoId + ")].ipOrigem");
        assertTrue(ips.stream().allMatch("127.0.0.1"::equals),
                "IP é o da conexão; X-Forwarded-For enviado pelo cliente é ignorado: " + ips);
        List<String> uas = JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='ABRIU_PDF' && @.usuarioId==" + alunoId + ")].userAgent");
        assertTrue(uas.contains(UA), "User-Agent da requisição");

        assertFalse(((List<?>) JsonPath.read(json,
                "$[?(@.conteudoId==" + videoId + " && @.acao=='INICIOU_VIDEO' && @.usuarioId==" + alunoId + ")]")).isEmpty(),
                "INICIOU_VIDEO registrado");
        assertFalse(((List<?>) JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='TENTATIVA_DOWNLOAD' && @.usuarioId==" + alunoId + ")]")).isEmpty());
        assertFalse(((List<?>) JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='TENTATIVA_PRINT_DETECTADA' && @.usuarioId==" + alunoId + ")]")).isEmpty());
        assertFalse(((List<?>) JsonPath.read(json,
                "$[?(@.conteudoId==" + pdfId + " && @.acao=='TENTATIVA_ACESSO_NEGADO' && @.usuarioId==" + alunoId + ")]")).isEmpty(),
                "acesso ao rascunho negado, auditado");
    }

    @Test
    @Order(12)
    @DisplayName("Despublicar bloqueia o aluno de novo; exclusão lógica bloqueia todos (404)")
    void test12_despublicaEExclui() throws Exception {
        definirStatus(pdfId, "RASCUNHO");
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/conteudos/{id}", videoId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", videoId).header("Authorization", bearer(adminTokenEst)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/conteudos/{id}/acesso", videoId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());
    }
}
