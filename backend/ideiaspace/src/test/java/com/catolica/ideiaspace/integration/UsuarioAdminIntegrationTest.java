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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gerenciamento administrativo de usuários: listagem restrita a ADMIN, edição por perfil,
 * ativação/desativação e alteração da própria senha.
 * Depende do administrador de bootstrap definido em src/test/resources/application.properties.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsuarioAdminIntegrationTest {

    private static final String ADMIN_EMAIL = "admin.integ@ideiaspace.com";
    private static final String ADMIN_SENHA = "admin_strong_password_123";
    private static final String SENHA_ALUNO = "senha_forte_teste_123";
    private static final String NOVA_SENHA = "nova_senha_forte_456";

    private static final String SUFIXO = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    private static final String ALUNO_EMAIL = "aluno.usuarios." + SUFIXO + "@ideiaspace.com";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    private static String adminToken;
    private static String alunoToken;
    private static long alunoId;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    private String login(String email, String senha) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\": \"%s\", \"senha\": \"%s\"}", email, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @Order(1)
    @DisplayName("Provisiona um aluno e obtém tokens de administrador e de aluno")
    void test01_setup() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_SENHA);

        mockMvc.perform(post("/api/v1/auth/provision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"nomeCompleto\": \"Aluno Usuarios\", \"email\": \"%s\", \"senha\": \"%s\", \"perfil\": \"ALUNO\"}",
                                ALUNO_EMAIL, SENHA_ALUNO)))
                .andExpect(status().isCreated());

        alunoToken = login(ALUNO_EMAIL, SENHA_ALUNO);
    }

    @Test
    @Order(2)
    @DisplayName("Aluno não lista usuários")
    void test02_alunoNaoListaUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + alunoToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("Administrador lista usuários sem expor senha ou hash")
    void test03_adminListaSemSegredos() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(corpo.contains("hash"), "resposta não pode conter hash");
        org.junit.jupiter.api.Assertions.assertFalse(corpo.contains("senha"), "resposta não pode conter senha");
        java.util.List<Integer> ids = JsonPath.read(corpo, "$[?(@.email == '" + ALUNO_EMAIL + "')].id");
        alunoId = ids.get(0).longValue();
    }

    @Test
    @Order(4)
    @DisplayName("Aluno não altera dados de outra conta")
    void test04_alunoNaoEditaOutraConta() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/{id}", alunoId + 1000000)
                        .header("Authorization", "Bearer " + alunoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeCompleto\": \"Invasor Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("Aluno não altera o próprio perfil")
    void test05_alunoNaoAlteraPerfil() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/{id}", alunoId)
                        .header("Authorization", "Bearer " + alunoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\": \"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("Administrador desativa e reativa um aluno")
    void test06_adminAlteraStatusDeAluno() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/{id}/status", alunoId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"statusConta\":\"INATIVA\"")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\": \"%s\", \"senha\": \"%s\"}", ALUNO_EMAIL, SENHA_ALUNO)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/status", alunoId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": true}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    @DisplayName("Administrador não desativa a própria conta")
    void test07_adminNaoDesativaProprioConta() throws Exception {
        MvcResult lista = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<Integer> ids = JsonPath.read(lista.getResponse().getContentAsString(),
                "$[?(@.email == '" + ADMIN_EMAIL + "')].id");
        long adminId = ids.get(0).longValue();

        mockMvc.perform(patch("/api/v1/usuarios/{id}/status", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(8)
    @DisplayName("Alteração de senha exige a senha atual correta")
    void test08_alteracaoDeSenhaExigeSenhaAtual() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/me/senha")
                        .header("Authorization", "Bearer " + alunoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"errada_123456\", \"novaSenha\": \"" + NOVA_SENHA + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/usuarios/me/senha")
                        .header("Authorization", "Bearer " + alunoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"" + SENHA_ALUNO + "\", \"novaSenha\": \"" + NOVA_SENHA + "\"}"))
                .andExpect(status().isNoContent());

        login(ALUNO_EMAIL, NOVA_SENHA);
    }

    @Test
    @Order(9)
    @DisplayName("Token emitido antes da desativação deixa de funcionar imediatamente")
    void test09_tokenDeContaDesativadaPerdeAcesso() throws Exception {
        String emailRevogado = "revogado." + SUFIXO + "@ideiaspace.com";
        mockMvc.perform(post("/api/v1/auth/provision")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"nomeCompleto\": \"Conta Revogada\", \"email\": \"%s\", \"senha\": \"%s\", \"perfil\": \"ALUNO\"}",
                                emailRevogado, SENHA_ALUNO)))
                .andExpect(status().isCreated());
        String tokenAntigo = login(emailRevogado, SENHA_ALUNO);

        MvcResult lista = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        java.util.List<Integer> ids = JsonPath.read(lista.getResponse().getContentAsString(),
                "$[?(@.email == '" + emailRevogado + "')].id");

        mockMvc.perform(patch("/api/v1/usuarios/{id}/status", ids.get(0).longValue())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ativo\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/usuarios/me/senha")
                        .header("Authorization", "Bearer " + tokenAntigo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\": \"" + SENHA_ALUNO + "\", \"novaSenha\": \"" + NOVA_SENHA + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    @DisplayName("E-mail já usado por outra conta retorna 409")
    void test10_emailDuplicadoRetornaConflito() throws Exception {
        MvcResult lista = mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = lista.getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(corpo, "$[?(@.email == '" + ALUNO_EMAIL + "')].id");

        mockMvc.perform(patch("/api/v1/usuarios/{id}", ids.get(0).longValue())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + ADMIN_EMAIL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("E-mail já cadastrado.")));
    }
}
