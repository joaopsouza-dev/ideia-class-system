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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 0: o provisionamento de contas exige autenticação com perfil ADMIN.
 * O administrador usado aqui é criado pelo bootstrap (APP_BOOTSTRAP_ADMIN_* em application.properties de teste).
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProvisionAccessIntegrationTest {

    private static final String PROVISION_URL = "/api/v1/auth/provision";
    private static final String ADMIN_EMAIL = "admin.integ@ideiaspace.com";
    private static final String ADMIN_SENHA = "admin_strong_password_123";
    private static final String SENHA_USUARIO = "senha_forte_teste_123";

    private static final String SUFIXO = UUID.randomUUID().toString().substring(0, 8).toLowerCase();
    private static final String ALUNO_EMAIL = "aluno.acesso." + SUFIXO + "@ideiaspace.com";
    private static final String DOCENTE_EMAIL = "docente.acesso." + SUFIXO + "@ideiaspace.com";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    private static String adminToken;
    private static String alunoToken;
    private static String docenteToken;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(springSecurityFilterChain)
                .build();
    }

    private static String corpoProvision(String nome, String email, String perfil) {
        return String.format("""
                {"nomeCompleto": "%s", "email": "%s", "senha": "%s", "perfil": "%s"}
                """, nome, email, SENHA_USUARIO, perfil);
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
    @DisplayName("Usuário anônimo não consegue provisionar conta, nem ADMIN")
    void test01_anonymousCannotProvision() throws Exception {
        mockMvc.perform(post(PROVISION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Invasor Teste", "invasor." + SUFIXO + "@ideiaspace.com", "ADMIN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("Administrador do bootstrap realiza login")
    void test02_bootstrapAdminLogin() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_SENHA);
    }

    @Test
    @Order(3)
    @DisplayName("ADMIN autenticado provisiona ALUNO e DOCENTE")
    void test03_adminProvisionsAlunoAndDocente() throws Exception {
        mockMvc.perform(post(PROVISION_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Aluno Acesso", ALUNO_EMAIL, "ALUNO")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PROVISION_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Docente Acesso", DOCENTE_EMAIL, "DOCENTE")))
                .andExpect(status().isCreated());

        alunoToken = login(ALUNO_EMAIL, SENHA_USUARIO);
        docenteToken = login(DOCENTE_EMAIL, SENHA_USUARIO);
    }

    @Test
    @Order(4)
    @DisplayName("ALUNO não consegue provisionar conta ADMIN (403)")
    void test04_alunoCannotProvisionAdmin() throws Exception {
        mockMvc.perform(post(PROVISION_URL)
                        .header("Authorization", "Bearer " + alunoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Escalada Aluno", "escalada.aluno." + SUFIXO + "@ideiaspace.com", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    @DisplayName("DOCENTE não consegue provisionar conta ADMIN (403)")
    void test05_docenteCannotProvisionAdmin() throws Exception {
        mockMvc.perform(post(PROVISION_URL)
                        .header("Authorization", "Bearer " + docenteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Escalada Docente", "escalada.docente." + SUFIXO + "@ideiaspace.com", "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    @DisplayName("DOCENTE não consegue provisionar nem ALUNO (403)")
    void test06_docenteCannotProvisionAluno() throws Exception {
        mockMvc.perform(post(PROVISION_URL)
                        .header("Authorization", "Bearer " + docenteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoProvision("Aluno Extra", "aluno.extra." + SUFIXO + "@ideiaspace.com", "ALUNO")))
                .andExpect(status().isForbidden());
    }
}
