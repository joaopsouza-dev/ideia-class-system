package com.catolica.ideiaspace.integration;

import com.catolica.ideiaspace.service.storage.ArquivoArmazenado;
import com.catolica.ideiaspace.service.storage.FileStorageService;
import com.catolica.ideiaspace.service.storage.ObjectStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.catolica.ideiaspace.integration.IntegracaoSupport.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consistência entre armazenamento de objetos e PostgreSQL, com falhas injetadas no
 * ObjectStorage (substituto controlado do bucket): falha de upload, falha de persistência e
 * substituição de arquivo. Não depende de Firebase real.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsistenciaArmazenamentoIntegrationTest {

    /** Decorator que delega ao armazenamento local e permite injetar falhas e observar chamadas. */
    static class StorageInstrumentado implements ObjectStorage {
        final FileStorageService real;
        final AtomicBoolean falharAoSalvar = new AtomicBoolean(false);
        final List<String> salvos = Collections.synchronizedList(new ArrayList<>());
        final List<String> removidos = Collections.synchronizedList(new ArrayList<>());

        StorageInstrumentado(FileStorageService real) {
            this.real = real;
        }

        void zerar() {
            falharAoSalvar.set(false);
            salvos.clear();
            removidos.clear();
        }

        @Override
        public ArquivoArmazenado salvar(MultipartFile arquivo) {
            if (falharAoSalvar.get()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha simulada no armazenamento.");
            }
            ArquivoArmazenado a = real.salvar(arquivo);
            salvos.add(a.storagePath());
            return a;
        }

        @Override
        public boolean existe(String p) {
            return real.existe(p);
        }

        @Override
        public void remover(String p) {
            removidos.add(p);
            real.remover(p);
        }

        @Override
        public Resource abrir(String p, long t) {
            return real.abrir(p, t);
        }

        @Override
        public UrlTemporaria gerarUrlLeitura(String p, String m, Duration v) {
            return real.gerarUrlLeitura(p, m, v);
        }

        @Override
        public String provedor() {
            return real.provedor();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        StorageInstrumentado storageInstrumentado(FileStorageService real) {
            return new StorageInstrumentado(real);
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    @Autowired
    private StorageInstrumentado storage;
    @Autowired
    private FileStorageService fisico;

    private MockMvc mvc;
    private IntegracaoSupport apoio;

    private static String docenteToken;
    private static Long alunoId;
    private static Long turmaId;
    private static Long materialId;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilter(springSecurityFilterChain).build();
        apoio = new IntegracaoSupport(mvc);
        apoio.loginAdmin();
        storage.zerar();
    }

    private int totalMateriais() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/conteudos").param("turmaId", String.valueOf(turmaId))
                        .header("Authorization", bearer(apoio.adminToken)))
                .andExpect(status().isOk()).andReturn();
        return ((List<?>) JsonPath.read(r.getResponse().getContentAsString(), "$")).size();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder cadastro(
            String token, String professorId, byte[] bytes) {
        var req = multipart("/api/v1/conteudos")
                .file(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", bytes))
                .header("Authorization", bearer(token))
                .param("turmaId", String.valueOf(turmaId))
                .param("titulo", "Consistência")
                .param("tipoMaterial", "PDF_APOSTILA");
        if (professorId != null) {
            req.param("professorId", professorId);
        }
        return req;
    }

    @Test
    @Order(1)
    @DisplayName("Preparação")
    void test01_setup() throws Exception {
        
        var docente = apoio.provisionar("DOCENTE", "docente.cs");
        var aluno = apoio.provisionar("ALUNO", "aluno.cs");
        docenteToken = docente.token();
        alunoId = aluno.id();
        turmaId = apoio.criarTurma("CS");
        apoio.vincular(turmaId, docente.id(), "DOCENTE");
        apoio.vincular(turmaId, aluno.id(), "DISCENTE");
    }

    @Test
    @Order(2)
    @DisplayName("Upload bem-sucedido: um objeto salvo, um registro, nenhuma remoção")
    void test02_uploadOk() throws Exception {
        MvcResult r = mvc.perform(cadastro(docenteToken, null, IntegracaoSupport.pdf("v1")))
                .andExpect(status().isCreated()).andReturn();
        materialId = IntegracaoSupport.idDe(r);
        assertEquals(1, storage.salvos.size());
        assertTrue(storage.removidos.isEmpty());
        assertTrue(fisico.existe(storage.salvos.get(0)));
        assertEquals(1, totalMateriais());
    }

    @Test
    @Order(3)
    @DisplayName("Falha no armazenamento: erro 502 e nenhum registro novo no PostgreSQL")
    void test03_falhaDeUpload() throws Exception {
        storage.falharAoSalvar.set(true);
        mvc.perform(cadastro(docenteToken, null, IntegracaoSupport.pdf("falha")))
                .andExpect(status().isBadGateway());
        assertTrue(storage.salvos.isEmpty());
        assertEquals(1, totalMateriais(), "nenhum registro órfão sem arquivo");
    }

    @Test
    @Order(4)
    @DisplayName("Falha na persistência: o objeto recém-gravado é removido (operação compensatória)")
    void test04_falhaDePersistencia() throws Exception {
        // Admin indica como autor um usuário que não é DOCENTE da turma: a função do banco recusa.
        MvcResult r = mvc.perform(cadastro(apoio.adminToken, String.valueOf(alunoId), IntegracaoSupport.pdf("orfao")))
                .andReturn();
        int s = r.getResponse().getStatus();
        assertTrue(s >= 400, "persistência deve falhar, status=" + s);

        assertEquals(1, storage.salvos.size(), "o arquivo chegou a ser gravado");
        String caminho = storage.salvos.get(0);
        assertEquals(List.of(caminho), storage.removidos, "e foi removido em seguida");
        assertFalse(fisico.existe(caminho), "nenhum objeto órfão");
        assertEquals(1, totalMateriais());
    }

    @Test
    @Order(5)
    @DisplayName("Substituição: novo arquivo gravado, antigo removido só após o commit, metadados atualizados")
    void test05_substituicaoOk() throws Exception {
        String antes = pathAtual();
        MvcResult r = mvc.perform(edicao(materialId, "Título novo", IntegracaoSupport.pdf("v2-diferente")))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, storage.salvos.size());
        String novo = storage.salvos.get(0);
        assertNotEquals(antes, novo);
        assertEquals(List.of(antes), storage.removidos);
        assertFalse(fisico.existe(antes));
        assertTrue(fisico.existe(novo));
        assertEquals("Título novo", JsonPath.read(r.getResponse().getContentAsString(), "$.titulo"));
    }

    @Test
    @Order(6)
    @DisplayName("Substituição com falha no armazenamento: arquivo anterior permanece intacto")
    void test06_substituicaoComFalha() throws Exception {
        String atual = pathAtual();
        storage.falharAoSalvar.set(true);
        mvc.perform(edicao(materialId, "Não deve mudar", IntegracaoSupport.pdf("v3")))
                .andExpect(status().isBadGateway());
        assertTrue(storage.removidos.isEmpty());
        assertTrue(fisico.existe(atual), "arquivo vigente preservado");
        assertEquals(atual, pathAtual());
    }

    @Test
    @Order(7)
    @DisplayName("Substituição por arquivo inválido: recusada antes de qualquer remoção; o antigo permanece")
    void test07_substituicaoInvalida() throws Exception {
        String atual = pathAtual();
        mvc.perform(edicao(materialId, "Inválido", "isto não é um PDF".getBytes()))
                .andExpect(status().isUnsupportedMediaType());
        assertTrue(storage.salvos.isEmpty());
        assertTrue(storage.removidos.isEmpty());
        assertTrue(fisico.existe(atual));
    }

    @Test
    @Order(8)
    @DisplayName("Exclusão lógica preserva o arquivo; o registro some da listagem")
    void test08_exclusaoLogica() throws Exception {
        String atual = pathAtual();
        mvc.perform(delete("/api/v1/conteudos/{id}", materialId).header("Authorization", bearer(docenteToken)))
                .andExpect(status().isNoContent());
        assertTrue(fisico.existe(atual), "soft delete mantém o objeto (auditoria/recuperação)");
        assertEquals(0, totalMateriais());
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder edicao(
            Long id, String titulo, byte[] bytes) {
        return multipart(HttpMethod.PUT, "/api/v1/conteudos/{id}", id)
                .file(new MockMultipartFile("arquivo", "n.pdf", "application/pdf", bytes))
                .header("Authorization", bearer(docenteToken))
                .param("titulo", titulo);
    }

    /** Caminho interno do material, obtido pela última gravação observada ou via arquivo no disco. */
    private String pathAtual() throws Exception {
        // O caminho não é exposto pela API; descobrimos o arquivo vigente pelo conteúdo servido.
        MvcResult acesso = mvc.perform(get("/api/v1/conteudos/{id}/acesso", materialId)
                        .header("Authorization", bearer(docenteToken)))
                .andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(acesso.getResponse().getContentAsString(), "$.url");
        String o = url.substring(url.indexOf("o=") + 2, url.indexOf("&e="));
        return java.net.URLDecoder.decode(o, java.nio.charset.StandardCharsets.UTF_8);
    }
}
