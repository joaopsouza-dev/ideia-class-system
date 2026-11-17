package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** URLs temporárias assinadas do provedor local (equivalente de desenvolvimento das Signed URLs). */
class FileStorageUrlTemporariaTest {

    private static final String CAMINHO = "materiais/2026/11/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf";

    @TempDir
    Path raiz;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        ReflectionTestUtils.setField(props, "root", raiz.toString());
        storage = new FileStorageService(props);
    }

    private static String param(String url, String nome) {
        return UriComponentsBuilder.fromUri(URI.create("http://x" + url)).build().getQueryParams().getFirst(nome);
    }

    private static String decodificado(String url, String nome) {
        return java.net.URLDecoder.decode(param(url, nome), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("URL gerada é válida, relativa à API e expira no prazo pedido")
    void urlValidaEExpiracao() {
        Instant antes = Instant.now();
        ObjectStorage.UrlTemporaria u = storage.gerarUrlLeitura(CAMINHO, "application/pdf", Duration.ofSeconds(300));

        assertTrue(u.url().startsWith("/api/v1/storage-local/arquivo?o="));
        assertEquals(CAMINHO, decodificado(u.url(), "o"));
        long exp = Long.parseLong(param(u.url(), "e"));
        assertEquals(u.expiraEm().getEpochSecond(), exp);
        assertTrue(exp >= antes.plusSeconds(299).getEpochSecond() && exp <= antes.plusSeconds(302).getEpochSecond());
        assertTrue(storage.assinaturaValida(CAMINHO, exp, param(u.url(), "s"), Instant.now()));
    }

    @Test
    @DisplayName("URL expirada é recusada (limite exato incluído)")
    void urlExpirada() {
        Instant exp = Instant.now().plusSeconds(60);
        String sig = param(storage.gerarUrlLeitura(CAMINHO, exp).url(), "s");

        assertTrue(storage.assinaturaValida(CAMINHO, exp.getEpochSecond(), sig, exp.minusSeconds(1)));
        assertFalse(storage.assinaturaValida(CAMINHO, exp.getEpochSecond(), sig, exp), "no instante da expiração já é inválida");
        assertFalse(storage.assinaturaValida(CAMINHO, exp.getEpochSecond(), sig, exp.plusSeconds(3600)));
    }

    @Test
    @DisplayName("Adulterar caminho, expiração ou assinatura invalida a URL")
    void adulteracao() {
        Instant exp = Instant.now().plusSeconds(600);
        String sig = param(storage.gerarUrlLeitura(CAMINHO, exp).url(), "s");
        long e = exp.getEpochSecond();
        Instant agora = Instant.now();

        assertFalse(storage.assinaturaValida("materiais/2026/11/outro.pdf", e, sig, agora), "outro objeto");
        assertFalse(storage.assinaturaValida(CAMINHO, e + 3600, sig, agora), "expiração estendida");
        assertFalse(storage.assinaturaValida(CAMINHO, e, sig.substring(1) + "A", agora), "assinatura alterada");
        assertFalse(storage.assinaturaValida(CAMINHO, e, "", agora));
        assertFalse(storage.assinaturaValida(CAMINHO, e, null, agora));
        assertFalse(storage.assinaturaValida(null, e, sig, agora));
    }

    @Test
    @DisplayName("Assinatura de uma instância não vale em outra (chaves diferentes)")
    void chaveDiferente() {
        StorageProperties props = new StorageProperties();
        ReflectionTestUtils.setField(props, "root", raiz.resolve("outro").toString());
        FileStorageService outra = new FileStorageService(props);

        Instant exp = Instant.now().plusSeconds(600);
        String sig = param(storage.gerarUrlLeitura(CAMINHO, exp).url(), "s");
        assertFalse(outra.assinaturaValida(CAMINHO, exp.getEpochSecond(), sig, Instant.now()));
    }

    @Test
    @DisplayName("Caminho com path traversal não é assinado nem aceito")
    void caminhoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.gerarUrlLeitura("../fora.pdf", "application/pdf", Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class,
                () -> storage.gerarUrlLeitura("/etc/passwd", "application/pdf", Duration.ofMinutes(5)));
        assertFalse(storage.assinaturaValida("../fora.pdf", Instant.now().plusSeconds(60).getEpochSecond(), "x", Instant.now()));
    }

    @Test
    @DisplayName("Prazo configurável é limitado entre 30 s e 3600 s")
    void ttlLimitado() {
        StorageProperties p = new StorageProperties();
        assertEquals(Duration.ofSeconds(900), p.getSignedUrlTtl(), "padrão");
        ReflectionTestUtils.setField(p, "signedUrlTtlSeconds", 5L);
        assertEquals(Duration.ofSeconds(StorageProperties.TTL_MINIMO_SEGUNDOS), p.getSignedUrlTtl());
        ReflectionTestUtils.setField(p, "signedUrlTtlSeconds", 604800L);
        assertEquals(Duration.ofSeconds(StorageProperties.TTL_MAXIMO_SEGUNDOS), p.getSignedUrlTtl());
        ReflectionTestUtils.setField(p, "signedUrlTtlSeconds", 120L);
        assertEquals(Duration.ofSeconds(120), p.getSignedUrlTtl());
    }
}
