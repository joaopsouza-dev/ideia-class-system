package com.catolica.ideiaspace.integration;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Preparação de dados reutilizada pelos testes de integração de visualização e consistência. */
final class IntegracaoSupport {

    static final String SENHA = "senha_forte_123";

    record Usuario(Long id, String token) {}

    private final MockMvc mvc;
    private final String sufixo = UUID.randomUUID().toString().substring(0, 6).toLowerCase();
    String adminToken;

    IntegracaoSupport(MockMvc mvc) {
        this.mvc = mvc;
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static byte[] pdf(String texto) {
        return ("%PDF-1.4\n" + texto + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] mp4() {
        byte[] b = new byte[2048];
        byte[] cab = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        System.arraycopy(cab, 0, b, 0, cab.length);
        return b;
    }

    static Long idDe(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    void loginAdmin() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin.integ@ideiaspace.com\",\"senha\":\"admin_strong_password_123\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
    }

    Usuario provisionar(String perfil, String prefixo) throws Exception {
        String email = prefixo + "." + sufixo + "@ideiaspace.com";
        MvcResult prov = mvc.perform(post("/api/v1/auth/provision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"nomeCompleto":"%s %s","email":"%s","senha":"%s","perfil":"%s"}
                                """, prefixo, sufixo, email, SENHA, perfil)))
                .andExpect(status().isCreated()).andReturn();
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"senha\":\"%s\"}", email, SENHA)))
                .andExpect(status().isOk()).andReturn();
        return new Usuario(idDe(prov), JsonPath.read(login.getResponse().getContentAsString(), "$.token"));
    }

    Long criarTurma(String prefixo) throws Exception {
        MvcResult disc = mvc.perform(post("/api/v1/disciplinas")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"codigo\":\"%s-%s\",\"nome\":\"%s %s\",\"descricao\":\"d\"}",
                                prefixo, sufixo, prefixo, sufixo)))
                .andExpect(status().isCreated()).andReturn();
        MvcResult turma = mvc.perform(post("/api/v1/turmas")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"disciplinaId\":%d,\"codigo\":\"%sT-%s\",\"nome\":\"Turma %s %s\",\"ano\":2026,\"semestre\":2,\"status\":\"ATIVA\"}",
                                idDe(disc), prefixo, sufixo, prefixo, sufixo)))
                .andExpect(status().isCreated()).andReturn();
        return idDe(turma);
    }

    void vincular(Long turmaId, Long usuarioId, String papel) throws Exception {
        mvc.perform(post("/api/v1/turmas/" + turmaId + "/participantes")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"usuarioId\":%d,\"papel\":\"%s\",\"inicioEm\":\"2026-01-01\"}", usuarioId, papel)))
                .andExpect(status().isCreated());
    }
}
