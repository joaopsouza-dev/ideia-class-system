package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de FirebaseObjectStorage SEM acesso ao bucket real e SEM credenciais reais.
 *
 * - Envio, remoção e falhas: usam um cliente Storage SIMULADO (java.lang.reflect.Proxy).
 *   Provam a lógica do backend (validação antes do envio, nome do objeto, opções, limpeza),
 *   NÃO provam que o Firebase real aceita o envio.
 * - Signed URL V4: usa o cliente real da biblioteca com uma chave RSA gerada na hora; a
 *   assinatura é calculada localmente, sem rede. Prova o formato e o prazo da URL, não que o
 *   bucket real a aceite.
 */
class FirebaseObjectStorageTest {

    private static final String BUCKET = "bucket-de-teste";

    /** Cliente Storage simulado: registra chamadas e permite injetar falhas. */
    private static final class FakeStorage implements InvocationHandler {
        final List<String> chamadas = new ArrayList<>();
        BlobInfo blobEnviado;
        byte[] bytesEnviados;
        Object[] opcoesEnvio;
        BlobId removido;
        RuntimeException falhaNoEnvio;
        RuntimeException falhaNaAssinatura;
        RuntimeException falhaNoGet;

        Storage storage() {
            return (Storage) Proxy.newProxyInstance(
                    Storage.class.getClassLoader(), new Class<?>[]{Storage.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "createFrom" -> {
                    chamadas.add("createFrom");
                    if (falhaNoEnvio != null) {
                        throw falhaNoEnvio;
                    }
                    blobEnviado = (BlobInfo) args[0];
                    bytesEnviados = Files.readAllBytes((Path) args[1]);
                    opcoesEnvio = (Object[]) args[2];
                    return null;
                }
                case "delete" -> {
                    chamadas.add("delete");
                    removido = (BlobId) args[0];
                    return true;
                }
                case "get" -> {
                    chamadas.add("get");
                    if (falhaNoGet != null) {
                        throw falhaNoGet;
                    }
                    return null; // objeto inexistente
                }
                case "signUrl" -> {
                    chamadas.add("signUrl");
                    if (falhaNaAssinatura != null) {
                        throw falhaNaAssinatura;
                    }
                    return null;
                }
                case "toString" -> {
                    return "FakeStorage";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    return m.getReturnType() == boolean.class ? Boolean.FALSE : null;
                }
            }
        }
    }

    private FakeStorage fake;
    private FirebaseObjectStorage storage;
    private StorageProperties props;
    private long temporariosAntes;

    private static Path dirTemporario() {
        return Path.of(System.getProperty("java.io.tmpdir"), "ideiaspace-upload");
    }

    private static long contarTemporarios() throws Exception {
        Path dir = dirTemporario();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        props = new StorageProperties();
        ReflectionTestUtils.setField(props, "maxPdfMb", 1L);
        ReflectionTestUtils.setField(props, "maxVideoMb", 2L);
        fake = new FakeStorage();
        storage = new FirebaseObjectStorage(fake.storage(), BUCKET, props);
        temporariosAntes = contarTemporarios();
    }

    private static byte[] pdf(int total) {
        byte[] b = new byte[total];
        byte[] h = "%PDF-1.4\nconteudo\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(h, 0, b, 0, h.length);
        return b;
    }

    private static byte[] mp4(int total) {
        byte[] b = new byte[total];
        byte[] cab = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
        System.arraycopy(cab, 0, b, 0, cab.length);
        return b;
    }

    private static int status(ResponseStatusException e) {
        return e.getStatusCode().value();
    }

    // ------------------------------------------------------------------ envio

    @Test
    @DisplayName("PDF válido: objeto privado em materiais/AAAA/MM/uuid.pdf, tipo e SHA-256 corretos, sem sobrescrita")
    void enviaPdf() throws Exception {
        byte[] dados = pdf(4096);
        ArquivoArmazenado r = storage.salvar(new MockMultipartFile("arquivo", "../x.exe", "text/plain", dados));

        assertTrue(r.storagePath().matches("materiais/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf"), r.storagePath());
        assertEquals("application/pdf", r.mimeType());
        assertEquals(dados.length, r.tamanhoBytes());
        assertEquals(64, r.sha256().length());

        assertEquals(BUCKET, fake.blobEnviado.getBucket());
        assertEquals(r.storagePath(), fake.blobEnviado.getName());
        assertEquals("application/pdf", fake.blobEnviado.getContentType());
        assertEquals(r.sha256(), fake.blobEnviado.getMetadata().get("sha256"));
        assertEquals("private, no-store", fake.blobEnviado.getCacheControl());
        assertArrayEquals(dados, fake.bytesEnviados);
        assertEquals(1, fake.opcoesEnvio.length, "pré-condição doesNotExist presente");
        assertEquals(temporariosAntes, contarTemporarios(), "temporário removido");
    }

    @Test
    @DisplayName("Vídeo MP4 válido é enviado com o tipo correto")
    void enviaVideo() throws Exception {
        byte[] dados = mp4(8192);
        ArquivoArmazenado r = storage.salvar(new MockMultipartFile("arquivo", "aula.mp4", "video/mp4", dados));

        assertTrue(r.storagePath().endsWith(".mp4"));
        assertEquals("video/mp4", fake.blobEnviado.getContentType());
        assertArrayEquals(dados, fake.bytesEnviados);
    }

    @Test
    @DisplayName("Arquivo inválido, vazio ou acima do limite é recusado ANTES de qualquer envio ao bucket")
    void recusaSemEnviar() throws Exception {
        ResponseStatusException invalido = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", "texto".getBytes())));
        assertEquals(415, status(invalido));

        ResponseStatusException vazio = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", new byte[0])));
        assertEquals(400, status(vazio));

        ResponseStatusException grande = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", pdf(1024 * 1024 + 1))));
        assertEquals(413, status(grande));

        assertFalse(fake.chamadas.contains("createFrom"), "nada foi enviado ao bucket");
        assertEquals(temporariosAntes, contarTemporarios(), "sem temporários órfãos");
    }

    @Test
    @DisplayName("Falha no envio: 502 sem detalhes internos, remoção compensatória e sem temporários")
    void falhaNoEnvio() throws Exception {
        fake.falhaNoEnvio = new StorageException(503, "backend indisponível");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", pdf(2048))));

        assertEquals(502, status(e));
        assertFalse(String.valueOf(e.getReason()).contains("backend indisponível"), "detalhe interno não vaza");
        assertTrue(fake.chamadas.contains("delete"), "tenta limpar objeto eventualmente finalizado");
        assertEquals(temporariosAntes, contarTemporarios());
    }

    @Test
    @DisplayName("Conflito de pré-condição (412) não apaga o objeto existente")
    void conflitoNaoApaga() {
        fake.falhaNoEnvio = new StorageException(412, "precondition failed");

        assertThrows(ResponseStatusException.class,
                () -> storage.salvar(new MockMultipartFile("arquivo", "a.pdf", "application/pdf", pdf(2048))));
        assertFalse(fake.chamadas.contains("delete"));
    }

    // ------------------------------------------------------------------ consulta / remoção

    @Test
    @DisplayName("existe: objeto ausente e nomes inválidos retornam false")
    void existe() {
        assertFalse(storage.existe("materiais/2026/11/nao-existe.pdf"));
        assertFalse(storage.existe("../materiais/x.pdf"));
        assertFalse(storage.existe("outro-prefixo/x.pdf"));
        assertFalse(storage.existe("materiais/../segredo.pdf"));
        assertFalse(storage.existe("materiais\\x.pdf"));
        assertFalse(storage.existe(null));
        assertFalse(storage.existe(""));
    }

    @Test
    @DisplayName("existe: indisponibilidade do bucket vira 502 (não é confundida com arquivo inexistente)")
    void existeComFalhaDoBucket() {
        fake.falhaNoGet = new StorageException(503, "indisponível");
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> storage.existe("materiais/2026/11/a.pdf"));
        assertEquals(502, status(e));
    }

    @Test
    @DisplayName("remover: apaga somente nomes válidos e nunca lança")
    void remover() {
        storage.remover("materiais/2026/11/a.pdf");
        assertEquals(BlobId.of(BUCKET, "materiais/2026/11/a.pdf"), fake.removido);

        fake.chamadas.clear();
        storage.remover("../../x");
        storage.remover(null);
        storage.remover("");
        assertTrue(fake.chamadas.isEmpty(), "nome inválido não chega ao bucket");
    }

    @Test
    @DisplayName("abrir e gerarUrlLeitura recusam nome de objeto inválido")
    void nomeInvalido() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> storage.abrir("../x.pdf", 1));
        assertThrows(IllegalArgumentException.class,
                () -> storage.gerarUrlLeitura("../x.pdf", "application/pdf", Duration.ofMinutes(5)));
        assertNotNull(storage.abrir("materiais/2026/11/a.pdf", 10));
        assertEquals(10, storage.abrir("materiais/2026/11/a.pdf", 10).contentLength());
    }

    @Test
    @DisplayName("Sem bucket configurado a inicialização falha com mensagem clara")
    void semBucket() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new FirebaseObjectStorage(fake.storage(), " ", props));
        assertTrue(e.getMessage().contains("FIREBASE_STORAGE_BUCKET"));
    }

    // ------------------------------------------------------------------ Signed URL V4

    @Test
    @DisplayName("Falha ao assinar vira 503 sem detalhes internos")
    void falhaAoAssinar() {
        fake.falhaNaAssinatura = new IllegalStateException("Signing key was not provided");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> storage.gerarUrlLeitura("materiais/2026/11/a.pdf", "application/pdf", Duration.ofMinutes(5)));
        assertEquals(503, status(e));
        assertFalse(String.valueOf(e.getReason()).contains("Signing key"));
    }

    private static Storage storageComChaveRsa() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair par = kpg.generateKeyPair();
        ServiceAccountCredentials credenciais = ServiceAccountCredentials.newBuilder()
                .setClientEmail("teste@projeto-de-teste.iam.gserviceaccount.com")
                .setPrivateKey(par.getPrivate())
                .setPrivateKeyId("chave-de-teste")
                .setProjectId("projeto-de-teste")
                .build();
        return StorageOptions.newBuilder().setProjectId("projeto-de-teste").setCredentials(credenciais).build().getService();
    }

    @Test
    @DisplayName("Signed URL V4 real: formato, host, assinatura RSA, leitura inline e expiração configurada")
    void signedUrlV4() throws Exception {
        FirebaseObjectStorage real = new FirebaseObjectStorage(storageComChaveRsa(), BUCKET, props);
        Instant antes = Instant.now();

        ObjectStorage.UrlTemporaria u = real.gerarUrlLeitura(
                "materiais/2026/11/a.pdf", "application/pdf", Duration.ofSeconds(300));

        String url = u.url();
        assertTrue(url.startsWith("https://storage.googleapis.com/" + BUCKET + "/materiais/2026/11/a.pdf?"), url);
        assertTrue(url.contains("X-Goog-Algorithm=GOOG4-RSA-SHA256"), "V4 com RSA-SHA256");
        assertTrue(url.contains("X-Goog-Signature="), "assinada");
        assertTrue(url.contains("X-Goog-Credential=teste%40projeto-de-teste.iam.gserviceaccount.com"), "conta de serviço do teste");
        assertTrue(url.contains("response-content-disposition=inline"), "exibição inline, não download forçado");
        assertTrue(url.contains("response-content-type=application%2Fpdf"));

        Matcher m = Pattern.compile("X-Goog-Expires=(\\d+)").matcher(url);
        assertTrue(m.find());
        assertEquals(300, Long.parseLong(m.group(1)), "validade gravada na própria assinatura");
        assertTrue(!u.expiraEm().isBefore(antes.plusSeconds(299)) && !u.expiraEm().isAfter(Instant.now().plusSeconds(301)));
    }

    @Test
    @DisplayName("Prazos diferentes geram assinaturas com expiração diferente (configurável)")
    void signedUrlPrazoConfiguravel() throws Exception {
        FirebaseObjectStorage real = new FirebaseObjectStorage(storageComChaveRsa(), BUCKET, props);

        String curta = real.gerarUrlLeitura("materiais/2026/11/a.pdf", "application/pdf", Duration.ofSeconds(60)).url();
        String longa = real.gerarUrlLeitura("materiais/2026/11/a.pdf", "application/pdf", Duration.ofSeconds(900)).url();

        assertTrue(curta.contains("X-Goog-Expires=60"));
        assertTrue(longa.contains("X-Goog-Expires=900"));
        assertNotEquals(curta, longa);
    }
}
