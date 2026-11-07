package com.catolica.ideiaspace.integration;

import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaRequestDTO;
import com.catolica.ideiaspace.dto.turma.TurmaRequestDTO;
import com.catolica.ideiaspace.dto.turma.TurmaUpdateDTO;
import com.catolica.ideiaspace.dto.turma.TurmaUsuarioRequestDTO;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TurmaAndParticipantesIntegrationTest {

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
    private static String alunoToken;
    private static Long alunoId;
    private static Long docenteId;
    private static Long disciplinaId;
    private static Long turmaId;
    private static Long vinculoDocenteId;

    private static final String UNIQUE_SUFFIX = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    private static final String DISC_CODE = "DISC-T-" + UNIQUE_SUFFIX;
    private static final String TURMA_CODE = "TURMA-" + UNIQUE_SUFFIX;

    @Test
    @Order(1)
    @DisplayName("Garante usuário admin e realiza login")
    void test01_loginAdmin() throws Exception {
        // Provisiona se for primeiro ambiente
        String provisionJson = """
            {
                "nomeCompleto": "Admin Turmas",
                "email": "admin.turmas@ideiaspace.com",
                "senha": "admin_strong_password_123",
                "perfil": "ADMIN"
            }
            """;
        mockMvc.perform(post("/api/v1/auth/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(provisionJson));

        // Login com admin integrado ou admin existente
        String loginJson = """
            {
                "email": "admin.integ@ideiaspace.com",
                "senha": "admin_strong_password_123"
            }
            """;
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andReturn();

        if (result.getResponse().getStatus() != 200) {
            // Tenta logar com admin.turmas
            result = mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(provisionJson))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        adminToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @Order(2)
    @DisplayName("Admin provisiona Aluno e Docente para os testes de Turma")
    void test02_provisionUsers() throws Exception {
        String alunoEmail = "aluno.turma." + UNIQUE_SUFFIX.toLowerCase() + "@ideiaspace.com";
        String alunoJson = String.format("""
            {
                "nomeCompleto": "Aluno Turma %s",
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

        // Login do aluno
        String alunoLoginJson = String.format("""
            {
                "email": "%s",
                "senha": "aluno_password_123"
            }
            """, alunoEmail);
        MvcResult alunoLoginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(alunoLoginJson))
                .andExpect(status().isOk())
                .andReturn();
        alunoToken = JsonPath.read(alunoLoginResult.getResponse().getContentAsString(), "$.token");

        // Provisiona docente
        String docenteEmail = "docente.turma." + UNIQUE_SUFFIX.toLowerCase() + "@ideiaspace.com";
        String docenteJson = String.format("""
            {
                "nomeCompleto": "Docente Turma %s",
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
    }

    @Test
    @Order(3)
    @DisplayName("Admin cria Disciplina para vincular as turmas")
    void test03_createDisciplina() throws Exception {
        String json = String.format("""
            {
                "codigo": "%s",
                "nome": "Disciplina Para Turmas %s",
                "descricao": "Base para turmas"
            }
            """, DISC_CODE, UNIQUE_SUFFIX);

        MvcResult result = mockMvc.perform(post("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        disciplinaId = id.longValue();
    }

    @Test
    @Order(4)
    @DisplayName("Admin cria Turma válida via POST /api/v1/turmas -> 201 Created")
    void test04_createTurma() throws Exception {
        String json = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "%s",
                "nome": "Turma de Algoritmos %s",
                "ano": 2026,
                "semestre": 1,
                "status": "ATIVA"
            }
            """, disciplinaId, TURMA_CODE, UNIQUE_SUFFIX);

        MvcResult result = mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigo", is(TURMA_CODE)))
                .andExpect(jsonPath("$.status", is("ATIVA")))
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        turmaId = id.longValue();
    }

    @Test
    @Order(5)
    @DisplayName("Validação: ano fora do intervalo (2000-2100) ou semestre inválido -> 400 Bad Request")
    void test05_invalidAnoSemestreReturns400() throws Exception {
        String invalidAnoJson = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "INV-ANO",
                "nome": "Turma Invalida",
                "ano": 1999,
                "semestre": 1
            }
            """, disciplinaId);

        mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidAnoJson))
                .andExpect(status().isBadRequest());

        String invalidSemestreJson = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "INV-SEM",
                "nome": "Turma Invalida",
                "ano": 2026,
                "semestre": 3
            }
            """, disciplinaId);

        mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidSemestreJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    @DisplayName("Validação: disciplina inexistente -> 404 Not Found")
    void test06_nonExistentDisciplinaReturns404() throws Exception {
        String json = """
            {
                "disciplinaId": 9999999,
                "codigo": "TURMA-ORFA",
                "nome": "Sem Disciplina",
                "ano": 2026,
                "semestre": 1
            }
            """;

        mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    @DisplayName("Validação: código duplicado de turma ativa -> 409 Conflict")
    void test07_duplicateCodigoReturns409() throws Exception {
        String json = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "%s",
                "nome": "Turma Com Codigo Repetido",
                "ano": 2026,
                "semestre": 2
            }
            """, disciplinaId, TURMA_CODE);

        mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(8)
    @DisplayName("Admin lista turmas e busca por ID -> 200 OK")
    void test08_adminListsAndGetsById() throws Exception {
        mockMvc.perform(get("/api/v1/turmas")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItem(TURMA_CODE)));

        mockMvc.perform(get("/api/v1/turmas/" + turmaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(turmaId.intValue())))
                .andExpect(jsonPath("$.codigo", is(TURMA_CODE)));
    }

    @Test
    @Order(9)
    @DisplayName("Admin atualiza turma via PUT /api/v1/turmas/{id} -> 200 OK")
    void test09_adminUpdatesTurma() throws Exception {
        String json = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "%s",
                "nome": "Turma Atualizada %s",
                "ano": 2026,
                "semestre": 1,
                "status": "ATIVA"
            }
            """, disciplinaId, TURMA_CODE, UNIQUE_SUFFIX);

        mockMvc.perform(put("/api/v1/turmas/" + turmaId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Turma Atualizada " + UNIQUE_SUFFIX)));
    }

    @Test
    @Order(10)
    @DisplayName("Admin vincula Aluno como DISCENTE e Docente como DOCENTE -> 201 Created")
    void test10_adminEnrollsParticipants() throws Exception {
        // Vincula Aluno
        String alunoVinculo = String.format("""
            {
                "usuarioId": %d,
                "papel": "DISCENTE",
                "inicioEm": "%s"
            }
            """, alunoId, LocalDate.now());

        MvcResult resultAluno = mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(alunoVinculo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId", is(alunoId.intValue())))
                .andExpect(jsonPath("$.papel", is("DISCENTE")))
                .andExpect(jsonPath("$.status", is("ATIVA")))
                .andReturn();

        Number vId = JsonPath.read(resultAluno.getResponse().getContentAsString(), "$.id");
        vinculoAlunoId = vId.longValue();

        // Vincula Docente
        String docenteVinculo = String.format("""
            {
                "usuarioId": %d,
                "papel": "DOCENTE",
                "inicioEm": "%s"
            }
            """, docenteId, LocalDate.now());

        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(docenteVinculo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId", is(docenteId.intValue())))
                .andExpect(jsonPath("$.papel", is("DOCENTE")));
    }

    @Test
    @Order(11)
    @DisplayName("Validação: duplicar vínculo ativo de usuário na turma -> 409 Conflict")
    void test11_duplicateParticipantReturns409() throws Exception {
        String duplicateVinculo = String.format("""
            {
                "usuarioId": %d,
                "papel": "DISCENTE"
            }
            """, alunoId);

        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(duplicateVinculo))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(12)
    @DisplayName("Validação: tentar vincular ALUNO com papel DOCENTE -> 400 Bad Request")
    void test12_alunoAsDocenteReturns400() throws Exception {
        String invalidRole = String.format("""
            {
                "usuarioId": %d,
                "papel": "DOCENTE"
            }
            """, alunoId);

        mockMvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRole))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("Admin lista participantes da turma -> 200 OK com ambos os vínculos")
    void test13_listParticipants() throws Exception {
        mockMvc.perform(get("/api/v1/turmas/" + turmaId + "/participantes")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].usuarioId", hasItems(alunoId.intValue(), docenteId.intValue())));
    }

    @Test
    @Order(14)
    @DisplayName("RLS: Aluno matriculado lista turmas e vê a turma à qual pertence")
    void test14_alunoSeesEnrolledTurma() throws Exception {
        mockMvc.perform(get("/api/v1/turmas")
                .header("Authorization", "Bearer " + alunoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", hasItem(TURMA_CODE)));
    }

    @Test
    @Order(15)
    @DisplayName("RLS: Aluno tenta criar turma -> Bloqueado com 403 Forbidden")
    void test15_alunoCannotCreateTurma() throws Exception {
        String json = String.format("""
            {
                "disciplinaId": %d,
                "codigo": "INVASAO-TURMA",
                "nome": "Turma Aluno Hacker",
                "ano": 2026,
                "semestre": 1
            }
            """, disciplinaId);

        mockMvc.perform(post("/api/v1/turmas")
                .header("Authorization", "Bearer " + alunoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(16)
    @DisplayName("Admin remove participante (vínculo aluno) -> 204 No Content")
    void test16_adminRemovesParticipant() throws Exception {
        mockMvc.perform(delete("/api/v1/turmas/" + turmaId + "/participantes/" + vinculoAlunoId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(17)
    @DisplayName("Admin exclui turma (soft-delete) via DELETE /api/v1/turmas/{id} -> 204 No Content")
    void test17_adminDeletesTurma() throws Exception {
        mockMvc.perform(delete("/api/v1/turmas/" + turmaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Após exclusão, busca retorna 404
        mockMvc.perform(get("/api/v1/turmas/" + turmaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
