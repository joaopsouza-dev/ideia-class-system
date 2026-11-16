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
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConteudoIntegrationTest {

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

    private static String adminToken;
    private static String docenteToken;
    private static Long docenteId;
    private static String alunoToken;
    private static Long alunoId;
    private static String alunoNaoMatriculadoToken;

    private static Long disciplinaId;
    private static Long turmaId;
    private static Long conteudoId;

    private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    @Test
    @Order(1)
    @DisplayName("Requisições para /api/v1/conteudos sem token JWT devem retornar 401")
    void test01_endpointsWithoutTokenReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error", containsString("Não autorizado")));

        mockMvc.perform(get("/api/v1/conteudos/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @Order(2)
    @DisplayName("Login de Administrador e preparação de usuários (Docente, Aluno Matriculado e Aluno Não Matriculado)")
    void test02_setupUsers() throws Exception {
        // Login com Admin
        String adminLoginJson = """
            {
                "email": "admin.integ@ideiaspace.com",
                "senha": "admin_strong_password_123"
            }
            """;
        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminLoginJson))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = JsonPath.read(adminResult.getResponse().getContentAsString(), "$.token");

        // Provisiona Docente
        String docenteEmail = "docente.mat." + UNIQUE_SUFFIX.toLowerCase() + "@ideiaspace.com";
        String docenteJson = String.format("""
            {
                "nomeCompleto": "Docente Materiais %s",
                "email": "%s",
                "senha": "docente_password_123",
                "perfil": "DOCENTE"
            }
            """, UNIQUE_SUFFIX, docenteEmail);
        MvcResult docenteResult = mockMvc.perform(post("/api/v1/auth/provision")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(docenteJson))
                .andExpect(status().isCreated())
                .andReturn();
        Number dId = JsonPath.read(docenteResult.getResponse().getContentAsString(), "$.id");
        docenteId = dId.longValue();

        MvcResult docenteLogin = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"senha\": \"docente_password_123\"}", docenteEmail)))
                .andExpect(status().isOk())
                .andReturn();
        docenteToken = JsonPath.read(docenteLogin.getResponse().getContentAsString(), "$.token");

        // Provisiona Aluno que será matriculado
        String alunoEmail = "aluno.mat." + UNIQUE_SUFFIX.toLowerCase() + "@ideiaspace.com";
        String alunoJson = String.format("""
            {
                "nomeCompleto": "Aluno Matriculado %s",
                "email": "%s",
                "senha": "aluno_password_123",
                "perfil": "ALUNO"
            }
            """, UNIQUE_SUFFIX, alunoEmail);
        MvcResult alunoResult = mockMvc.perform(post("/api/v1/auth/provision")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(alunoJson))
                .andExpect(status().isCreated())
                .andReturn();
        Number aId = JsonPath.read(alunoResult.getResponse().getContentAsString(), "$.id");
        alunoId = aId.longValue();

        MvcResult alunoLogin = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"senha\": \"aluno_password_123\"}", alunoEmail)))
                .andExpect(status().isOk())
                .andReturn();
        alunoToken = JsonPath.read(alunoLogin.getResponse().getContentAsString(), "$.token");

        // Provisiona Aluno que NÃO será matriculado nesta turma
        String aluno2Email = "aluno.outra." + UNIQUE_SUFFIX.toLowerCase() + "@ideiaspace.com";
        String aluno2Json = String.format("""
            {
                "nomeCompleto": "Aluno Outra Turma %s",
                "email": "%s",
                "senha": "aluno_password_123",
                "perfil": "ALUNO"
            }
            """, UNIQUE_SUFFIX, aluno2Email);
        mockMvc.perform(post("/api/v1/auth/provision")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(aluno2Json))
                .andExpect(status().isCreated());

        MvcResult aluno2Login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"senha\": \"aluno_password_123\"}", aluno2Email)))
                .andExpect(status().isOk())
                .andReturn();
        alunoNaoMatriculadoToken = JsonPath.read(aluno2Login.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @Order(3)
    @DisplayName("Admin cria Disciplina, Turma e vincula Docente e Aluno")
    void test03_createDisciplinaTurmaAndParticipantes() throws Exception {
        // Cria Disciplina
        String discJson = String.format("""
            {
                "codigo": "MAT-%s",
                "nome": "Sistemas de Arquivos e Armazenamento %s",
                "descricao": "Disciplina para testes de integração de materiais."
            }
            """, UNIQUE_SUFFIX, UNIQUE_SUFFIX);
        MvcResult discRes = mockMvc.perform(post("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(discJson))
                .andExpect(status().isCreated())
                .andReturn();
        Number dId = JsonPath.read(discRes.getResponse().getContentAsString(), "$.id");
        disciplinaId = dId.longValue();

        // Cria Turma
        String turmaJson = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "TURMA-%s",
                "nome": "Turma Alpha %s",
                "ano": 2026,
                "semestre": 2,
                "status": "ATIVA"
            }
            """, disciplinaId, UNIQUE_SUFFIX, UNIQUE_SUFFIX);
        MvcResult turmaRes = mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(turmaJson))
                .andExpect(status().isCreated())
                .andReturn();
        Number tId = JsonPath.read(turmaRes.getResponse().getContentAsString(), "$.id");
        turmaId = tId.longValue();

        // Vincula Docente
        String docVinculo = String.format("""
            {
                "usuarioId": %d,
                "papel": "DOCENTE",
                "inicioEm": "2026-01-01"
            }
            """, docenteId);
        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(docVinculo))
                .andExpect(status().isCreated());

        // Vincula Aluno
        String alunoVinculo = String.format("""
            {
                "usuarioId": %d,
                "papel": "DISCENTE",
                "inicioEm": "2026-01-01"
            }
            """, alunoId);
        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(alunoVinculo))
                .andExpect(status().isCreated());
    }

    private static final byte[] PDF_BYTES =
            "%PDF-1.4\nconteudo de teste\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    @Order(4)
    @DisplayName("Aluno não pode cadastrar conteúdo (retorna 403)")
    void test04_alunoCannotCreateContent() throws Exception {
        mockMvc.perform(multipart("/api/v1/conteudos")
                .file(new org.springframework.mock.web.MockMultipartFile("arquivo", "a.pdf", "application/pdf", PDF_BYTES))
                .header("Authorization", "Bearer " + alunoToken)
                .param("turmaId", turmaId.toString())
                .param("titulo", "Tentativa de Aluno")
                .param("tipoMaterial", "PDF_APOSTILA"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Admin cadastra conteúdo (upload real) associando ao professor docente da turma")
    void test05_adminCreatesContent() throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/v1/conteudos")
                .file(new org.springframework.mock.web.MockMultipartFile("arquivo", "apostila.pdf", "application/pdf", PDF_BYTES))
                .header("Authorization", "Bearer " + adminToken)
                .param("turmaId", turmaId.toString())
                .param("professorId", docenteId.toString())
                .param("titulo", "Apostila Arquitetura de Software " + UNIQUE_SUFFIX)
                .param("descricao", "Conteúdo didático oficial para a disciplina.")
                .param("tipoMaterial", "PDF_APOSTILA")
                .param("statusPublicacao", "PUBLICADO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.titulo", containsString("Apostila Arquitetura de Software")))
                .andExpect(jsonPath("$.tipoMaterial").value("PDF_APOSTILA"))
                .andExpect(jsonPath("$.statusPublicacao").value("PUBLICADO"))
                .andExpect(jsonPath("$.turmaId").value(turmaId))
                .andReturn();

        Number cId = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        conteudoId = cId.longValue();
    }

    @Test
    @Order(6)
    @DisplayName("Admin lista todos os conteúdos e lista filtrando por turmaId")
    void test06_adminListConteudo() throws Exception {
        // Listagem geral
        mockMvc.perform(get("/api/v1/conteudos")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Listagem filtrada por turmaId
        mockMvc.perform(get("/api/v1/conteudos")
                .header("Authorization", "Bearer " + adminToken)
                .param("turmaId", turmaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conteudoId))
                .andExpect(jsonPath("$[0].turmaId").value(turmaId));
    }

    @Test
    @Order(7)
    @DisplayName("Admin busca conteúdo por ID com sucesso")
    void test07_adminGetById() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + conteudoId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conteudoId))
                .andExpect(jsonPath("$.turmaId").value(turmaId))
                .andExpect(jsonPath("$.titulo", containsString("Apostila Arquitetura de Software")));
    }

    @Test
    @Order(8)
    @DisplayName("Docente da turma lista conteúdos e vê o material")
    void test08_docenteListConteudo() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos")
                .header("Authorization", "Bearer " + docenteToken)
                .param("turmaId", turmaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conteudoId));
    }

    @Test
    @Order(9)
    @DisplayName("Docente da turma busca conteúdo por ID com sucesso")
    void test09_docenteGetById() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + conteudoId)
                .header("Authorization", "Bearer " + docenteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conteudoId));
    }

    @Test
    @Order(10)
    @DisplayName("Aluno matriculado na turma lista conteúdos e vê o material")
    void test10_alunoMatriculadoListConteudo() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos")
                .header("Authorization", "Bearer " + alunoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.id == " + conteudoId + ")]").exists());
    }

    @Test
    @Order(11)
    @DisplayName("Aluno matriculado busca conteúdo por ID com sucesso")
    void test11_alunoMatriculadoGetById() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + conteudoId)
                .header("Authorization", "Bearer " + alunoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conteudoId))
                .andExpect(jsonPath("$.turmaId").value(turmaId));
    }

    @Test
    @Order(12)
    @DisplayName("Aluno NÃO matriculado na turma não vê o material na listagem (RLS isola)")
    void test12_alunoNaoMatriculadoDoesNotSeeContent() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos")
                .header("Authorization", "Bearer " + alunoNaoMatriculadoToken)
                .param("turmaId", turmaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(13)
    @DisplayName("Aluno NÃO matriculado ao buscar ID do material recebe 404 (RLS oculta o conteúdo)")
    void test13_alunoNaoMatriculadoGetByIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/" + conteudoId)
                .header("Authorization", "Bearer " + alunoNaoMatriculadoToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @Order(14)
    @DisplayName("Busca por ID inexistente retorna 404")
    void test14_notFoundForNonExistentId() throws Exception {
        mockMvc.perform(get("/api/v1/conteudos/999999")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
