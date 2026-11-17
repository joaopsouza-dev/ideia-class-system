package com.catolica.ideiaspace.service.storage;

import com.catolica.ideiaspace.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validação do upload, compartilhada por TODOS os provedores de armazenamento:
 * detecta o tipo pelo CONTEÚDO (assinatura binária), impõe o limite de tamanho durante a
 * cópia e calcula o SHA-256, gravando o resultado em um arquivo temporário privado.
 * Quem chama é responsável por mover/enviar e depois apagar o temporário.
 */
public final class UploadInspector {

    private static final Logger log = LoggerFactory.getLogger(UploadInspector.class);

    private static final int TAMANHO_CABECALHO = 1024;
    private static final int TAMANHO_BUFFER = 64 * 1024;

    private UploadInspector() {}

    /** Arquivo já validado, ainda em local temporário. */
    public record Inspecionado(Path temporario, TipoArquivo tipo, long tamanhoBytes, String sha256) {}

    public static Inspecionado inspecionar(
            MultipartFile arquivo, Path diretorioTemporario, StorageProperties props) {

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

            long limite = tipo.isPdf() ? props.getMaxPdfBytes() : props.getMaxVideoBytes();
            long limiteMb = tipo.isPdf() ? props.getMaxPdfMb() : props.getMaxVideoMb();

            Files.createDirectories(diretorioTemporario);
            parcial = Files.createTempFile(diretorioTemporario, "upload-", ".part");
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
            if (total > limite) {
                // Cabeçalho maior que o limite (limites minúsculos de teste): mesma regra do laço.
                throw new ResponseStatusException(HttpStatusCode.valueOf(413),
                        "O arquivo excede o limite de " + limiteMb + " MB para este tipo de material.");
            }

            concluido = true;
            return new Inspecionado(parcial, tipo, total, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            log.error("Falha de E/S ao inspecionar material", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível gravar o arquivo. Tente novamente.");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        } finally {
            if (!concluido && parcial != null) {
                descartar(parcial);
            }
        }
    }

    /** Apaga o temporário sem lançar. */
    public static void descartar(Path temporario) {
        if (temporario == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporario);
        } catch (IOException e) {
            log.warn("Não foi possível remover arquivo temporário {}", temporario.getFileName());
        }
    }
}
