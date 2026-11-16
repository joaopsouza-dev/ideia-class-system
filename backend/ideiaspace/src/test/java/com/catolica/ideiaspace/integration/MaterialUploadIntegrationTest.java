package com.catolica.ideiaspace.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Etapa 3 — upload, validação de arquivos, permissões, edição, substituição, publicação e
 * exclusão de materiais. Usa o mesmo banco de testes e o mesmo estilo dos demais testes de
 * integração (admin criado pelo bootstrap controlado em test/resources/application.properties).
 *
 * Limites configurados para os testes: PDF 1 MB e vídeo 2 MB.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MaterialUploadIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    private static final String SUFIXO = UUID.randomUUID().toString().substring(0, 6).toLowerCase();
    private static final String SENHA = "senha_forte_123";

    private static String adminToken;
    private static String docenteToken;      // autor dos materiais
    private static Long docenteId;
    private static String colegaToken;       // outro DOCENTE da mesma turma
    private static String estranhoToken;     // DOCENTE sem vínculo com a turma
    private static String alunoToken;        // aluno matriculado
    private static String alunoForaToken;    // aluno de outra turma

    private static Long turmaId;
    private static Long pdfId;
    private static Long videoId;
    private static Long adminMaterialId;

    private static final byte[] PDF_V1 = pdf("conteudo-versao-1");
    private static final byte[] PDF_V2 = pdf("conteudo-versao-2-diferente");

    // ------------------------------------------------------------------ helpers

    private static byte[] pdf(String texto) {
        return ("%PDF-1.4\n" + texto + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] mp4() {
        byte[] b = new byte[2048];
        byte[] cab = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        System.arraycopy(cab, 0, b, 0, cab.length);
        return b;
    }

    private static byte[] webm() {
        byte[] b = new byte[512];
        byte[] cab = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x82, (byte) 0x84,
                'w', 'e', 'b', 'm'};
        System.arraycopy(cab, 0, b, 0, cab.length);
        return b;
    }

    private static String sha256(byte[] dados) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
    }

    private static MockMultipartFile arquivo(String nome, String contentType, byte[] bytes) {
        return new MockMultipartFile("arquivo", nome, contentType, bytes);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private MockMultipartHttpServletRequestBuilder cadastro(String token, MockMultipartFile file, String tipo, String status) {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/v1/conteudos")
                .header("Authorization", bearer(token))
                .param("turmaId", String.valueOf(turmaId))
                .param("titulo", "Material " + UUID.randomUUID().toString().substring(0, 6))
                .param("descricao", "Descrição do material")
                .param("tipoMaterial", tipo);
        if (status != null) {
            req.param("statusPublicacao", status);
        }
        if (file != null) {
            req.file(file);
        }
        return req;
    }

    private MockMultipartHttpServletRequestBuilder edicao(String token, Long id, String titulo, MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder req = multipart(HttpMethod.PUT, "/api/v1/conteudos/{id}", id);
        req.header("Authorization", bearer(token)).param("titulo", titulo).param("descricao", "Descrição editada");
        if (file != null) {
            req.file(file);
        }
        return req;
    }

    private String provisionar(String perfil, String prefixo, Long[] idOut) throws Exception {
        String email = prefixo + "." + SUFIXO + "@ideiaspace.com";
        MvcResult prov = mockMvc.perform(post("/api/v1/auth/provision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"nomeCompleto":"%s %s","email":"%s","senha":"%s","perfil":"%s"}
                                """, prefixo, SUFIXO, email, SENHA, perfil)))
                .andExpect(status().isCreated())
                .andReturn();
        if (idOut != null) {
            idOut[0] = ((Number) JsonPath.read(prov.getResponse().getContentAsString(), "$.id")).longValue();
        }
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", email, SENHA)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }

    private void vincular(Long usuarioId, String papel) throws Exception {
        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"usuarioId\":%d,\"papel\":\"%s\",\"inicioEm\":\"2026-01-01\"}", usuarioId, papel)))
                .andExpect(status().isCreated());
    }

    private Long idDe(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ------------------------------------------------------------------ preparação

    @Test
    @Order(1)
    @DisplayName("Preparação: admin, docente autor, docente colega, docente estranho, aluno e aluno de fora")
    void test01_setup() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.integ@ideiaspace.com\",\"senha\":\"admin_strong_password_123\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken = JsonPath.read(login.getResponse().getContentAsString(), "$.token");

        Long[] docId = new Long[1];
        Long[] colegaId = new Long[1];
        Long[] alunoId = new Long[1];
        docenteToken = provisionar("DOCENTE", "docente.up", docId);
        docenteId = docId[0];
        colegaToken = provisionar("DOCENTE", "colega.up", colegaId);
        estranhoToken = provisionar("DOCENTE", "estranho.up", null);
        alunoToken = provisionar("ALUNO", "aluno.up", alunoId);
        alunoForaToken = provisionar("ALUNO", "alunofora.up", null);

        MvcResult disc = mockMvc.perform(post("/api/v1/disciplinas")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"codigo\":\"UP-%s\",\"nome\":\"Upload %s\",\"descricao\":\"d\"}", SUFIXO, SUFIXO)))
                .andExpect(status().isCreated()).andReturn();
        MvcResult turma = mockMvc.perform(post("/api/v1/turmas")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"disciplinaId\":%d,\"codigo\":\"UPT-%s\",\"nome\":\"Turma Upload %s\",\"ano\":2026,\"semestre\":2,\"status\":\"ATIVA\"}",
                                idDe(disc), SUFIXO, SUFIXO)))
                .andExpect(status().isCreated()).andReturn();
        turmaId = idDe(turma);

        vincular(docId[0], "DOCENTE");
        vincular(colegaId[0], "DOCENTE");
        vincular(alunoId[0], "DISCENTE");
    }

    // ------------------------------------------------------------------ cadastro / upload

    @Test
    @Order(2)
    @DisplayName("Cadastro sem token retorna 401")
    void test02_semToken() throws Exception {
        mockMvc.perform(multipart("/api/v1/conteudos").file(arquivo("a.pdf", "application/pdf", PDF_V1))
                        .param("turmaId", String.valueOf(turmaId)).param("titulo", "Sem token")
                        .param("tipoMaterial", "PDF_APOSTILA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("Aluno não pode cadastrar material (403)")
    void test03_alunoNaoCadastra() throws Exception {
        mockMvc.perform(cadastro(alunoToken, arquivo("a.pdf", "application/pdf", PDF_V1), "PDF_APOSTILA", "PUBLICADO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    @DisplayName("Docente sem vínculo DOCENTE com a turma não pode cadastrar (403)")
    void test04_docenteSemVinculoNaoCadastra() throws Exception {
        mockMvc.perform(cadastro(estranhoToken, arquivo("a.pdf", "application/pdf", PDF_V1), "PDF_APOSTILA", "PUBLICADO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Docente vinculado cadastra PDF real: 201, RASCUNHO por padrão, metadados vêm do servidor e nada de caminho na resposta")
    void test05_docenteCadastraPdf() throws Exception {
        // O cliente declara Content-Type mentiroso e nome malicioso: o servidor ignora ambos.
        MvcResult r = mockMvc.perform(cadastro(docenteToken,
                        arquivo("../../apostila.exe", "text/plain", PDF_V1), "PDF_APOSTILA", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.turmaId").value(turmaId))
                .andExpect(jsonPath("$.professorId").value(docenteId))
                .andExpect(jsonPath("$.tipoMaterial").value("PDF_APOSTILA"))
                .andExpect(jsonPath("$.statusPublicacao").value("RASCUNHO"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.tamanhoBytes").value(PDF_V1.length))
                .andExpect(jsonPath("$.sha256").value(sha256(PDF_V1)))
                .andExpect(jsonPath("$.storagePath").doesNotExist())
                .andExpect(jsonPath("$.caminho").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist())
                .andReturn();
        pdfId = idDe(r);
    }

    @Test
    @Order(6)
    @DisplayName("Arquivo inválido é rejeitado pelo conteúdo, não pelo nome/Content-Type (415)")
    void test06_arquivoInvalido() throws Exception {
        mockMvc.perform(cadastro(docenteToken,
                        arquivo("falso.pdf", "application/pdf", "<html>nao sou pdf</html>".getBytes(StandardCharsets.UTF_8)),
                        "PDF_APOSTILA", "RASCUNHO"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error", containsString("Formato não aceito")));

        mockMvc.perform(cadastro(docenteToken,
                        arquivo("falso.mp4", "video/mp4", "texto qualquer".getBytes(StandardCharsets.UTF_8)),
                        "VIDEO_AULA", "RASCUNHO"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @Order(7)
    @DisplayName("Arquivo vazio e ausente retornam 400")
    void test07_vazioEAusente() throws Exception {
        mockMvc.perform(cadastro(docenteToken, arquivo("a.pdf", "application/pdf", new byte[0]), "PDF_APOSTILA", "RASCUNHO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(cadastro(docenteToken, null, "PDF_APOSTILA", "RASCUNHO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("Arquivo acima do limite do tipo retorna 413 (PDF > 1 MB nos testes)")
    void test08_acimaDoLimite() throws Exception {
        byte[] grande = new byte[1024 * 1024 + 1];
        byte[] cab = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(cab, 0, grande, 0, cab.length);
        mockMvc.perform(cadastro(docenteToken, arquivo("grande.pdf", "application/pdf", grande), "PDF_LIVRO", "RASCUNHO"))
                .andExpect(status().is(413));
    }

    @Test
    @Order(9)
    @DisplayName("Tipo do material incompatível com o arquivo retorna 400 (PDF como videoaula e vídeo como PDF)")
    void test09_tipoIncompativel() throws Exception {
        mockMvc.perform(cadastro(docenteToken, arquivo("a.pdf", "application/pdf", PDF_V1), "VIDEO_AULA", "RASCUNHO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(cadastro(docenteToken, arquivo("v.mp4", "video/mp4", mp4()), "PDF_LIVRO", "RASCUNHO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(10)
    @DisplayName("Campos inválidos: título curto, tipo desconhecido e turma inexistente")
    void test10_camposInvalidos() throws Exception {
        mockMvc.perform(multipart("/api/v1/conteudos").file(arquivo("a.pdf", "application/pdf", PDF_V1))
                        .header("Authorization", bearer(docenteToken))
                        .param("turmaId", String.valueOf(turmaId)).param("titulo", "x").param("tipoMaterial", "PDF_LIVRO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/conteudos").file(arquivo("a.pdf", "application/pdf", PDF_V1))
                        .header("Authorization", bearer(docenteToken))
                        .param("turmaId", String.valueOf(turmaId)).param("titulo", "Titulo ok").param("tipoMaterial", "AUDIO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/conteudos").file(arquivo("a.pdf", "application/pdf", PDF_V1))
                        .header("Authorization", bearer(docenteToken))
                        .param("turmaId", "999999").param("titulo", "Titulo ok").param("tipoMaterial", "PDF_LIVRO"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    @DisplayName("Docente cadastra vídeo MP4 e outro WebM válidos")
    void test11_docenteCadastraVideos() throws Exception {
        MvcResult mp4 = mockMvc.perform(cadastro(docenteToken, arquivo("aula.mp4", "application/octet-stream", mp4()), "VIDEO_AULA", "RASCUNHO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoMaterial").value("VIDEO_AULA"))
                .andExpect(jsonPath("$.mimeType").value("video/mp4"))
                .andExpect(jsonPath("$.tamanhoBytes").value(2048))
                .andReturn();
        videoId = idDe(mp4);

        mockMvc.perform(cadastro(docenteToken, arquivo("aula.webm", "video/webm", webm()), "VIDEO_AULA", "RASCUNHO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mimeType").value("video/webm"));
    }

    // ------------------------------------------------------------------ rascunho x publicado

    @Test
    @Order(12)
    @DisplayName("Rascunho: aluno não vê na listagem e recebe 404 por ID; autor e admin enxergam")
    void test12_rascunhoInvisivelAoAluno() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos").header("Authorization", bearer(alunoToken)).param("turmaId", String.valueOf(turmaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusPublicacao").value("RASCUNHO"));
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @Order(13)
    @DisplayName("Rascunho de um docente não é visível a outro docente da mesma turma")
    void test13_rascunhoInvisivelAoColega() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(colegaToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    @DisplayName("Arquivo de rascunho: aluno recebe 404 e requisição sem token recebe 401")
    void test14_arquivoRestrito() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(15)
    @DisplayName("Autor lê o arquivo gravado (bytes idênticos, sem cache, sem sniffing) e suporta Range")
    void test15_autorLeArquivo() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn();
        assertArrayEquals(PDF_V1, r.getResponse().getContentAsByteArray());

        mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo")
                        .header("Authorization", bearer(docenteToken)).header("Range", "bytes=0-4"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("%PDF-".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @Order(16)
    @DisplayName("Aluno não pode publicar/despublicar; colega docente não pode publicar material alheio")
    void test16_publicacaoNegada() throws Exception {
        String corpo = "{\"statusPublicacao\":\"PUBLICADO\"}";
        // aluno: o rascunho é invisível => 404
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(alunoToken))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
        // colega docente: o rascunho do outro é invisível => 404
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(colegaToken))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(17)
    @DisplayName("Status inválido ou ausente retorna 400")
    void test17_statusInvalido() throws Exception {
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"ARQUIVADO\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(18)
    @DisplayName("Autor publica: aluno matriculado passa a ver; aluno de outra turma continua sem ver")
    void test18_autorPublica() throws Exception {
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"PUBLICADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPublicacao").value("PUBLICADO"));

        mockMvc.perform(get("/api/v1/conteudos").header("Authorization", bearer(alunoToken)).param("turmaId", String.valueOf(turmaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pdfId));
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoForaToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(19)
    @DisplayName("Material publicado: aluno e colega docente recebem 403 ao editar/publicar/excluir/ler arquivo")
    void test19_naoAutorRecebe403() throws Exception {
        for (String token : new String[]{alunoToken, colegaToken}) {
            mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"RASCUNHO\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(edicao(token, pdfId, "Tentativa de edição", null))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
        }
        // e o material segue intacto e publicado
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", not("Tentativa de edição")));
    }

    @Test
    @Order(20)
    @DisplayName("Autor despublica: aluno volta a não ver o material")
    void test20_autorDespublica() throws Exception {
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"RASCUNHO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusPublicacao").value("RASCUNHO"));
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ edição e substituição

    @Test
    @Order(21)
    @DisplayName("Autor edita título e descrição sem trocar o arquivo (arquivo e hash preservados)")
    void test21_editaMetadados() throws Exception {
        mockMvc.perform(edicao(docenteToken, pdfId, "Título editado pelo autor", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título editado pelo autor"))
                .andExpect(jsonPath("$.descricao").value("Descrição editada"))
                .andExpect(jsonPath("$.sha256").value(sha256(PDF_V1)))
                .andExpect(jsonPath("$.statusPublicacao").value("RASCUNHO"));
        MvcResult r = mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk()).andReturn();
        assertArrayEquals(PDF_V1, r.getResponse().getContentAsByteArray());
    }

    @Test
    @Order(22)
    @DisplayName("Edição com título inválido retorna 400")
    void test22_editaTituloInvalido() throws Exception {
        mockMvc.perform(edicao(docenteToken, pdfId, "x", null)).andExpect(status().isBadRequest());
    }

    @Test
    @Order(23)
    @DisplayName("Substituição de arquivo: novo conteúdo, novo tamanho e novo hash; leitura devolve os bytes novos")
    void test23_substituiArquivo() throws Exception {
        mockMvc.perform(edicao(docenteToken, pdfId, "Título após substituição", arquivo("v2.pdf", "application/pdf", PDF_V2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título após substituição"))
                .andExpect(jsonPath("$.tamanhoBytes").value(PDF_V2.length))
                .andExpect(jsonPath("$.sha256").value(sha256(PDF_V2)))
                .andExpect(jsonPath("$.storagePath").doesNotExist());
        MvcResult r = mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk()).andReturn();
        assertArrayEquals(PDF_V2, r.getResponse().getContentAsByteArray());
    }

    @Test
    @Order(24)
    @DisplayName("Substituir PDF por vídeo (ou arquivo inválido) é recusado e o arquivo anterior permanece")
    void test24_substituicaoInvalida() throws Exception {
        mockMvc.perform(edicao(docenteToken, pdfId, "Não deve mudar", arquivo("v.mp4", "video/mp4", mp4())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(edicao(docenteToken, pdfId, "Não deve mudar", arquivo("x.pdf", "application/pdf", "lixo".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Título após substituição"))
                .andExpect(jsonPath("$.sha256").value(sha256(PDF_V2)));
        MvcResult r = mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk()).andReturn();
        assertArrayEquals(PDF_V2, r.getResponse().getContentAsByteArray());
    }

    @Test
    @Order(25)
    @DisplayName("Não é possível trocar o tipo entre PDF e vídeo na edição (400)")
    void test25_trocaDeFamiliaNegada() throws Exception {
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/conteudos/{id}", pdfId)
                        .header("Authorization", bearer(docenteToken))
                        .param("titulo", "Titulo ok").param("tipoMaterial", "VIDEO_AULA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(26)
    @DisplayName("Substitui o vídeo por outro vídeo (WebM no lugar de MP4)")
    void test26_substituiVideo() throws Exception {
        mockMvc.perform(edicao(docenteToken, videoId, "Videoaula substituída", arquivo("n.webm", "video/webm", webm())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mimeType").value("video/webm"))
                .andExpect(jsonPath("$.tamanhoBytes").value(512));
    }

    // ------------------------------------------------------------------ administrador

    @Test
    @Order(27)
    @DisplayName("Admin cadastra informando o professor autor; sem professorId retorna 400")
    void test27_adminCadastra() throws Exception {
        mockMvc.perform(cadastro(adminToken, arquivo("a.pdf", "application/pdf", PDF_V1), "PDF_LIVRO", "PUBLICADO"))
                .andExpect(status().isBadRequest());

        MvcResult r = mockMvc.perform(cadastro(adminToken, null, "PDF_LIVRO", "PUBLICADO")
                        .file(arquivo("a.pdf", "application/pdf", PDF_V1))
                        .param("professorId", String.valueOf(docenteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.professorId").value(docenteId))
                .andExpect(jsonPath("$.statusPublicacao").value("PUBLICADO"))
                .andReturn();
        adminMaterialId = idDe(r);
    }

    @Test
    @Order(28)
    @DisplayName("Admin edita, despublica e lê o arquivo de material de qualquer professor")
    void test28_adminGerencia() throws Exception {
        mockMvc.perform(edicao(adminToken, adminMaterialId, "Editado pelo admin", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Editado pelo admin"));
        mockMvc.perform(patch("/api/v1/conteudos/" + adminMaterialId + "/status").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"RASCUNHO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/conteudos/" + adminMaterialId + "/arquivo").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ exclusão lógica

    @Test
    @Order(29)
    @DisplayName("Colega docente não exclui material alheio; o autor exclui (204) e o material some das consultas")
    void test29_exclusao() throws Exception {
        mockMvc.perform(patch("/api/v1/conteudos/" + pdfId + "/status").header("Authorization", bearer(docenteToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statusPublicacao\":\"PUBLICADO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(colegaToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(alunoToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conteudos/" + pdfId + "/arquivo").header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conteudos").header("Authorization", bearer(alunoToken)).param("turmaId", String.valueOf(turmaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + pdfId + ")]").doesNotExist());
        // excluir de novo / editar excluído => 404
        mockMvc.perform(delete("/api/v1/conteudos/" + pdfId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(edicao(docenteToken, pdfId, "Titulo ok", null)).andExpect(status().isNotFound());
    }

    @Test
    @Order(30)
    @DisplayName("Admin exclui material de qualquer professor (exclusão lógica)")
    void test30_adminExclui() throws Exception {
        mockMvc.perform(delete("/api/v1/conteudos/" + adminMaterialId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/conteudos/" + adminMaterialId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }
}
