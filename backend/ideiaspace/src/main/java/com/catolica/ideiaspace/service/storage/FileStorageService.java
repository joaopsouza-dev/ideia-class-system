package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.AppSecurityProperties;
import com.catolica.ideiaspace.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Armazenamento local privado dos materiais (provedor "local": desenvolvimento e testes).
 *
 * - Valida o arquivo pelo conteúdo (assinatura) e impõe o limite de tamanho durante a cópia
 *   (ver {@link UploadInspector}, compartilhado com o provedor Firebase).
 * - Grava em arquivo temporário e só então move atomicamente para o destino final.
 * - O nome final é gerado pelo servidor (UUID); o nome enviado pelo cliente é descartado.
 * - O caminho persistido é relativo à raiz e nunca é exposto pela API.
 * - Todo acesso a um caminho persistido é resolvido contra a raiz (proteção contra path traversal).
 * - URLs temporárias: assinadas por HMAC-SHA256 (caminho + expiração), validadas pelo
 *   LocalStorageController. Reproduzem, em desenvolvimento, o comportamento das Signed URLs do GCS.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class FileStorageService implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final String PASTA_TEMPORARIA = ".tmp";
    private static final String PASTA_MATERIAIS = "materiais";
    private static final String ROTA_URL_LOCAL = "/api/v1/storage-local/arquivo";
    private static final String CONTEXTO_CHAVE = "ideiaspace-local-storage-url-v1";

    private final StorageProperties props;
    private final Path raiz;
    private final Path temporario;
    private final byte[] chaveAssinatura;

    /** Construtor padrão do Spring: a chave das URLs temporárias deriva do segredo JWT. */
    @Autowired
    public FileStorageService(StorageProperties props, AppSecurityProperties seguranca) {
        this(props, derivarChave(seguranca.getJwtSecret()));
    }

    /** Usado em testes unitários: chave aleatória por instância. */
    public FileStorageService(StorageProperties props) {
        this(props, chaveAleatoria());
    }

    private FileStorageService(StorageProperties props, byte[] chaveAssinatura) {
        this.props = props;
        this.chaveAssinatura = chaveAssinatura;
        this.raiz = Path.of(props.getRoot()).toAbsolutePath().normalize();
        this.temporario = raiz.resolve(PASTA_TEMPORARIA);
        try {
            Files.createDirectories(temporario);
            if (!Files.isWritable(raiz)) {
                throw new IllegalStateException("Diretório de armazenamento sem permissão de escrita: " + raiz);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível preparar o armazenamento em " + raiz, e);
        }
        log.info("Armazenamento privado de materiais pronto em {}", raiz);
    }

    @Override
    public String provedor() {
        return "local";
    }

    /** Valida e grava o arquivo. Lança ResponseStatusException (400/413/415) se inválido. */
    @Override
    public ArquivoArmazenado salvar(MultipartFile arquivo) {
        UploadInspector.Inspecionado inspecionado = UploadInspector.inspecionar(arquivo, temporario, props);
        Path parcial = inspecionado.temporario();
        boolean concluido = false;
        try {
            TipoArquivo tipo = inspecionado.tipo();
            LocalDate hoje = LocalDate.now();
            String relativo = PASTA_MATERIAIS + "/" + hoje.getYear() + "/"
                    + String.format("%02d", hoje.getMonthValue()) + "/"
                    + UUID.randomUUID() + "." + tipo.extensao();
            Path destino = resolverSeguro(relativo);
            Files.createDirectories(destino.getParent());
            restringirPermissoes(parcial);
            mover(parcial, destino);
            concluido = true;

            return new ArquivoArmazenado(relativo, tipo, tipo.mimeType(),
                    inspecionado.tamanhoBytes(), inspecionado.sha256());
        } catch (IOException e) {
            log.error("Falha de E/S ao gravar material", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível gravar o arquivo. Tente novamente.");
        } finally {
            if (!concluido) {
                UploadInspector.descartar(parcial);
            }
        }
    }

    /** Resolve um caminho persistido para um Path absoluto dentro da raiz. */
    public Path resolver(String storagePath) {
        return resolverSeguro(storagePath);
    }

    @Override
    public boolean existe(String storagePath) {
        try {
            return Files.isRegularFile(resolverSeguro(storagePath));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Remoção "melhor esforço": nunca lança; falhas são apenas registradas. */
    @Override
    public void remover(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            Files.deleteIfExists(resolverSeguro(storagePath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Não foi possível remover arquivo do armazenamento: {}",
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public Resource abrir(String storagePath, long tamanhoBytes) {
        return new FileSystemResource(resolverSeguro(storagePath));
    }

    // ------------------------------------------------------------------ URLs temporárias

    @Override
    public UrlTemporaria gerarUrlLeitura(String storagePath, String mimeType, Duration validade) {
        resolverSeguro(storagePath); // recusa caminho inválido antes de assinar
        Instant expiraEm = Instant.now().plus(validade);
        return gerarUrlLeitura(storagePath, expiraEm);
    }

    /** Variante com expiração explícita (permite testar URLs já expiradas). */
    public UrlTemporaria gerarUrlLeitura(String storagePath, Instant expiraEm) {
        resolverSeguro(storagePath);
        long exp = expiraEm.getEpochSecond();
        String url = ROTA_URL_LOCAL
                + "?o=" + URLEncoder.encode(storagePath, StandardCharsets.UTF_8)
                + "&e=" + exp
                + "&s=" + assinar(storagePath, exp);
        return new UrlTemporaria(url, Instant.ofEpochSecond(exp));
    }

    /** Verifica assinatura, expiração e validade do caminho de uma URL temporária local. */
    public boolean assinaturaValida(String storagePath, long expiraEmEpoch, String assinatura, Instant agora) {
        if (!StringUtils.hasText(storagePath) || !StringUtils.hasText(assinatura)) {
            return false;
        }
        if (expiraEmEpoch <= agora.getEpochSecond()) {
            return false;
        }
        try {
            resolverSeguro(storagePath);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] esperado = assinar(storagePath, expiraEmEpoch).getBytes(StandardCharsets.US_ASCII);
        byte[] recebido = assinatura.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperado, recebido);
    }

    private String assinar(String storagePath, long expiraEmEpoch) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chaveAssinatura, "HmacSHA256"));
            mac.update(("v1\n" + storagePath + "\n" + expiraEmEpoch).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }

    private static byte[] derivarChave(String segredo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(CONTEXTO_CHAVE.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }

    private static byte[] chaveAleatoria() {
        byte[] chave = new byte[32];
        new SecureRandom().nextBytes(chave);
        return chave;
    }

    // ------------------------------------------------------------------ apoio

    private Path resolverSeguro(String storagePath) {
        if (!StringUtils.hasText(storagePath) || storagePath.contains("\\") || storagePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido.");
        }
        Path relativo = Path.of(storagePath);
        if (relativo.isAbsolute()) {
            throw new IllegalArgumentException("Caminho de armazenamento inválido.");
        }
        Path resolvido = raiz.resolve(relativo).normalize();
        if (!resolvido.startsWith(raiz) || resolvido.startsWith(temporario)) {
            throw new IllegalArgumentException("Caminho de armazenamento fora da raiz permitida.");
        }
        return resolvido;
    }

    private static void mover(Path origem, Path destino) throws IOException {
        try {
            Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(origem, destino);
        }
    }

    private static void restringirPermissoes(Path arquivo) {
        try {
            Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Sistema de arquivos sem POSIX (ex.: Windows em desenvolvimento): segue sem restringir.
        }
    }
}
