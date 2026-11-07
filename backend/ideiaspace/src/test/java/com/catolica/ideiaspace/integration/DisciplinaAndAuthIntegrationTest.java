package com.catolica.ideiaspace.integration;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.dto.auth.LoginRequestDTO;
import com.catolica.ideiaspace.dto.auth.ProvisionRequestDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaRequestDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaUpdateDTO;
import com.catolica.ideiaspace.security.TokenService;
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
class DisciplinaAndAuthIntegrationTest {

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

    @Autowired
    private TokenService tokenService;

    private static String adminToken;
    private static String alunoToken;
    private static Long disciplinaId;
    private static final String UNIQUE_CODE = "MAT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    @Test
    @Order(1)
    @DisplayName("Provisionamento do primeiro administrador via /api/v1/auth/provision")
    void test01_provisionInitialAdmin() throws Exception {
        String json = """
            {
                "nomeCompleto": "Admin Integracao",
                "email": "admin.integ@ideiaspace.com",
                "senha": "admin_strong_password_123",
                "perfil": "ADMIN"
            }
            """;

        // Pode retornar 201 se for o primeiro, ou 403 se já existir admin
        mockMvc.perform(post("/api/v1/auth/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    @Order(2)
    @DisplayName("Login com credenciais inválidas retorna 401")
    void test02_loginWrongCredentialsReturns401() throws Exception {
        String json = """
            {
                "email": "inexistente@ideiaspace.com",
                "senha": "senha_incorreta_total"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("Login como administrador retorna token JWT válido")
    void test03_loginAdminReturnsToken() throws Exception {
        String json = """
            {
                "email": "admin.integ@ideiaspace.com",
                "senha": "admin_strong_password_123"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.usuario.perfil", is("ADMIN")))
                .andReturn();

        adminToken = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @Order(4)
    @DisplayName("Admin autenticado provisiona usuário comum (ALUNO)")
    void test04_adminProvisionsAluno() throws Exception {
        String json = """
            {
                "nomeCompleto": "Aluno Integracao",
                "email": "aluno.integ@ideiaspace.com",
                "senha": "aluno_strong_password_123",
                "perfil": "ALUNO"
            }
            """;

        mockMvc.perform(post("/api/v1/auth/provision")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));

        // Realiza login do aluno para obter o token
        String loginJson = """
            {
                "email": "aluno.integ@ideiaspace.com",
                "senha": "aluno_strong_password_123"
            }
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil", is("ALUNO")))
                .andReturn();

        alunoToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @Order(5)
    @DisplayName("Acesso a /api/v1/disciplinas sem token é rejeitado com 401")
    void test05_getDisciplinasWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/disciplinas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @Order(6)
    @DisplayName("Acesso com token adulterado/inválido é rejeitado com 401")
    void test06_getDisciplinasWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/disciplinas")
                .header("Authorization", "Bearer token_totalmente_invalido.12345"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @Order(7)
    @DisplayName("ADMIN cria disciplina via POST /api/v1/disciplinas (permitido pelas políticas RLS)")
    void test07_adminCreatesDisciplina() throws Exception {
        String json = String.format("""
            {
                "codigo": "%s",
                "nome": "Estrutura de Dados Avançada",
                "descricao": "Disciplina para teste de RLS"
            }
            """, UNIQUE_CODE);

        MvcResult result = mockMvc.perform(post("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.codigo", is(UNIQUE_CODE)))
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        disciplinaId = id.longValue();
    }

    @Test
    @Order(8)
    @DisplayName("ADMIN lista disciplinas e localiza a cadastrada")
    void test08_adminListsDisciplinas() throws Exception {
        mockMvc.perform(get("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].codigo", hasItem(UNIQUE_CODE)));
    }

    @Test
    @Order(9)
    @DisplayName("ADMIN busca disciplina por ID com 200 OK")
    void test09_adminGetsDisciplinaById() throws Exception {
        mockMvc.perform(get("/api/v1/disciplinas/" + disciplinaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(disciplinaId.intValue())))
                .andExpect(jsonPath("$.codigo", is(UNIQUE_CODE)));
    }

    @Test
    @Order(10)
    @DisplayName("ADMIN atualiza disciplina com sucesso via PUT")
    void test10_adminUpdatesDisciplina() throws Exception {
        String json = String.format("""
            {
                "codigo": "%s",
                "nome": "Estrutura de Dados II (Atualizado)",
                "descricao": "Descrição atualizada pelo admin"
            }
            """, UNIQUE_CODE);

        mockMvc.perform(put("/api/v1/disciplinas/" + disciplinaId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Estrutura de Dados II (Atualizado)")));
    }

    @Test
    @Order(11)
    @DisplayName("Tentativa de cadastrar disciplina com código duplicado retorna 409 Conflict")
    void test11_duplicateCodeReturns409() throws Exception {
        String json = String.format("""
            {
                "codigo": "%s",
                "nome": "Outra com mesmo código",
                "descricao": "Deve conflitar"
            }
            """, UNIQUE_CODE);

        mockMvc.perform(post("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @Order(12)
    @DisplayName("ALUNO tenta criar disciplina -> Bloqueado pelo RLS do PostgreSQL com 403 Forbidden")
    void test12_alunoTriesToCreateDisciplina_blockedByRls403() throws Exception {
        String json = """
            {
                "codigo": "INVASAO-01",
                "nome": "Tentativa por Aluno",
                "descricao": "Não deve passar"
            }
            """;

        mockMvc.perform(post("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + alunoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @Order(13)
    @DisplayName("ALUNO lista disciplinas -> Vê apenas disciplinas às quais pertence (não vê a criada pelo admin)")
    void test13_alunoListsDisciplinas_filteredByRls() throws Exception {
        mockMvc.perform(get("/api/v1/disciplinas")
                .header("Authorization", "Bearer " + alunoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", not(hasItem(UNIQUE_CODE))));
    }

    @Test
    @Order(14)
    @DisplayName("ALUNO tenta atualizar disciplina sem permissão -> Retorna 404 (invisível ao aluno) ou 403")
    void test14_alunoTriesToUpdateDisciplina_denied() throws Exception {
        String json = String.format("""
            {
                "codigo": "%s",
                "nome": "Tentativa de Edição por Aluno",
                "descricao": "Não deve funcionar"
            }
            """, UNIQUE_CODE);

        mockMvc.perform(put("/api/v1/disciplinas/" + disciplinaId)
                .header("Authorization", "Bearer " + alunoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().is(oneOf(403, 404)));
    }

    @Test
    @Order(15)
    @DisplayName("ADMIN exclui disciplina via DELETE -> 204 No Content")
    void test15_adminDeletesDisciplina() throws Exception {
        mockMvc.perform(delete("/api/v1/disciplinas/" + disciplinaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Após exclusão, busca retorna 404
        mockMvc.perform(get("/api/v1/disciplinas/" + disciplinaId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
