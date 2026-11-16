package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Testes do armazenamento local sem Spring Boot e sem banco de dados. */
class FileStorageServiceTest {

    @TempDir
    Path raiz;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        ReflectionTestUtils.setField(props, "root", raiz.toString());
        ReflectionTestUtils.setField(props, "maxPdfMb", 1L);
        ReflectionTestUtils.setField(props, "maxVideoMb", 2L);
        storage = new FileStorageService(props);
    }

    private static byte[] pdf(int totalBytes) {
        byte[] b = new byte[totalBytes];
        byte[] h = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(h, 0, b, 0, h.length);
        return b;
    }

    private static int status(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    private long arquivosFinais() throws IOException {
        try (Stream<Path> s = Files.walk(raiz)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    @Test
    @DisplayName("Salva PDF válido: caminho relativo gerado pelo servidor, tamanho e SHA-256 corretos")
    void salvaPdf() throws Exception {
        byte[] conteudo = pdf(5000);
        MockMultipartFile mf = new MockMultipartFile("arquivo", "../../etc/passwd.exe", "text/plain", conteudo);

        ArquivoArmazenado r = storage.salvar(mf);

        assertEquals(TipoArquivo.PDF, r.tipo());
        assertEquals("application/pdf", r.mimeType());
        assertEquals(5000, r.tamanhoBytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo)), r.sha256());
        assertTrue(r.storagePath().startsWith("materiais/"));
        assertTrue(r.storagePath().endsWith(".pdf"));
        assertFalse(r.storagePath().contains("passwd"), "nome enviado pelo cliente não pode vazar para o caminho");
        assertFalse(r.storagePath().startsWith("/"));
        assertArrayEquals(conteudo, Files.readAllBytes(storage.resolver(r.storagePath())));
    }

    @Test
    @DisplayName("Ignora Content-Type e extensão enviados: texto com nome .pdf e tipo application/pdf é rejeitado (415)")
    void rejeitaFalsoPdf() throws Exception {
        MockMultipartFile mf = new MockMultipartFile("arquivo", "falso.pdf", "application/pdf",
                "isto nao e um pdf".getBytes(StandardCharsets.UTF_8));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> storage.salvar(mf));
        assertEquals(415, status(e));
        assertEquals(0, arquivosFinais());
    }

    @Test
    @DisplayName("Rejeita arquivo vazio e ausente (400)")
    void rejeitaVazio() {
        ResponseStatusException vazio = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[0])));
        assertEquals(400, status(vazio));
        ResponseStatusException nulo = assertThrows(ResponseStatusException.class, () -> storage.salvar(null));
        assertEquals(400, status(nulo));
    }

    @Test
    @DisplayName("Impõe o limite de tamanho do PDF durante a cópia (413) e não deixa resíduo em disco")
    void limitePdf() throws Exception {
        MockMultipartFile grande = new MockMultipartFile("arquivo", "g.pdf", "application/pdf", pdf(1024 * 1024 + 1));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> storage.salvar(grande));
        assertEquals(413, status(e));
        assertEquals(0, arquivosFinais(), "arquivo parcial deve ser descartado");

        MockMultipartFile limite = new MockMultipartFile("arquivo", "l.pdf", "application/pdf", pdf(1024 * 1024));
        assertEquals(1024 * 1024, storage.salvar(limite).tamanhoBytes(), "exatamente no limite é aceito");
    }

    @Test
    @DisplayName("Vídeo tem limite próprio, maior que o do PDF")
    void limiteVideo() {
        byte[] mp4 = new byte[1024 * 1024 + 10]; // acima do limite de PDF (1 MB), abaixo do de vídeo (2 MB)
        byte[] cab = DetectorTipoArquivoTest.mp4("isom");
        System.arraycopy(cab, 0, mp4, 0, cab.length);
        ArquivoArmazenado r = storage.salvar(new MockMultipartFile("arquivo", "v.mp4", "video/mp4", mp4));
        assertEquals(TipoArquivo.MP4, r.tipo());
        assertTrue(r.storagePath().endsWith(".mp4"));

        byte[] enorme = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(cab, 0, enorme, 0, cab.length);
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "v.mp4", "video/mp4", enorme)));
        assertEquals(413, status(e));
    }

    @Test
    @DisplayName("Proteção contra path traversal ao resolver caminhos persistidos")
    void pathTraversal() {
        assertThrows(IllegalArgumentException.class, () -> storage.resolver("../fora.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver("materiais/../../fora.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver("materiais\\..\\fora.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver(".tmp/upload-1.part"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver(""));
        assertThrows(IllegalArgumentException.class, () -> storage.resolver(null));
    }

    @Test
    @DisplayName("remover apaga o arquivo e nunca lança, mesmo para caminho inválido ou inexistente")
    void remover() {
        ArquivoArmazenado r = storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", pdf(100)));
        assertTrue(storage.existe(r.storagePath()));
        storage.remover(r.storagePath());
        assertFalse(storage.existe(r.storagePath()));
        assertDoesNotThrow(() -> storage.remover(r.storagePath()));
        assertDoesNotThrow(() -> storage.remover("../../x"));
        assertDoesNotThrow(() -> storage.remover(null));
    }
}
