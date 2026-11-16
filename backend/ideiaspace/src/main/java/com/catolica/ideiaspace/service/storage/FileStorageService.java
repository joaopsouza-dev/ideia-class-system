package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Armazenamento local privado dos materiais.
 *
 * - Valida o arquivo pelo conteúdo (assinatura) e impõe o limite de tamanho durante a cópia.
 * - Grava em arquivo temporário e só então move atomicamente para o destino final.
 * - O nome final é gerado pelo servidor (UUID); o nome enviado pelo cliente é descartado.
 * - O caminho persistido é relativo à raiz e nunca é exposto pela API.
 * - Todo acesso a um caminho persistido é resolvido contra a raiz (proteção contra path traversal).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final int TAMANHO_CABECALHO = 1024;
    private static final int TAMANHO_BUFFER = 64 * 1024;
    private static final String PASTA_TEMPORARIA = ".tmp";
    private static final String PASTA_MATERIAIS = "materiais";

    private final Path raiz;
    private final Path temporario;
    private final long limitePdfBytes;
    private final long limiteVideoBytes;
    private final long limitePdfMb;
    private final long limiteVideoMb;

    public FileStorageService(StorageProperties props) {
        this.raiz = Path.of(props.getRoot()).toAbsolutePath().normalize();
        this.temporario = raiz.resolve(PASTA_TEMPORARIA);
        this.limitePdfBytes = props.getMaxPdfBytes();
        this.limiteVideoBytes = props.getMaxVideoBytes();
        this.limitePdfMb = props.getMaxPdfMb();
        this.limiteVideoMb = props.getMaxVideoMb();
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

    /** Valida e grava o arquivo. Lança ResponseStatusException (400/413/415) se inválido. */
    public ArquivoArmazenado salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O arquivo do material é obrigatório e não pode estar vazio.");
        }

        Path parcial = null;
        boolean concluido = false;
        try (InputStream entrada = arquivo.getInputStream()) {
            byte[] cabecalho = entrada.readNBytes(TAMANHO_CABECALHO);
            TipoArquivo tipo = DetectorTipoArquivo.detectar(cabecalho)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                            "Formato não aceito. Envie um PDF, um vídeo MP4 ou um vídeo WebM válido."));

            long limite = tipo.isPdf() ? limitePdfBytes : limiteVideoBytes;
            long limiteMb = tipo.isPdf() ? limitePdfMb : limiteVideoMb;

            parcial = Files.createTempFile(temporario, "upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = cabecalho.length;

            try (OutputStream saida = Files.newOutputStream(parcial)) {
                saida.write(cabecalho);
                digest.update(cabecalho);

                byte[] buffer = new byte[TAMANHO_BUFFER];
                int lidos;
                while ((lidos = entrada.read(buffer)) != -1) {
                    total += lidos;
                    if (total > limite) {
                        throw new ResponseStatusException(HttpStatusCode.valueOf(413),
                                "O arquivo excede o limite de " + limiteMb + " MB para este tipo de material.");
                    }
                    saida.write(buffer, 0, lidos);
                    digest.update(buffer, 0, lidos);
                }
            }

            LocalDate hoje = LocalDate.now();
            String relativo = PASTA_MATERIAIS + "/" + hoje.getYear() + "/"
                    + String.format("%02d", hoje.getMonthValue()) + "/"
                    + UUID.randomUUID() + "." + tipo.extensao();
            Path destino = resolverSeguro(relativo);
            Files.createDirectories(destino.getParent());
            restringirPermissoes(parcial);
            mover(parcial, destino);
            concluido = true;

            return new ArquivoArmazenado(relativo, tipo, tipo.mimeType(), total,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            log.error("Falha de E/S ao gravar material", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível gravar o arquivo. Tente novamente.");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        } finally {
            if (!concluido && parcial != null) {
                try {
                    Files.deleteIfExists(parcial);
                } catch (IOException e) {
                    log.warn("Não foi possível remover arquivo temporário {}", parcial.getFileName());
                }
            }
        }
    }

    /** Resolve um caminho persistido para um Path absoluto dentro da raiz. */
    public Path resolver(String storagePath) {
        return resolverSeguro(storagePath);
    }

    public boolean existe(String storagePath) {
        try {
            return Files.isRegularFile(resolverSeguro(storagePath));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Remoção "melhor esforço": nunca lança; falhas são apenas registradas. */
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
