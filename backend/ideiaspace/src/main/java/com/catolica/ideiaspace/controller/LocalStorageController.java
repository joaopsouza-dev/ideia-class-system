package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.service.storage.FileStorageService;
import com.catolica.ideiaspace.service.storage.TipoArquivo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

/**
 * Endpoint de leitura das URLs temporárias do provedor "local" (desenvolvimento/testes).
 * Equivale, para o ambiente sem Firebase, ao que o GCS faz com uma Signed URL: a autorização
 * está na própria URL (HMAC com caminho + expiração), emitida antes pelo backend somente para
 * quem tem acesso ao material. Não existe quando APP_STORAGE_PROVIDER=firebase.
 */
@RestController
@RequestMapping("/api/v1/storage-local")
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageController {

    private final FileStorageService storage;

    public LocalStorageController(FileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/arquivo")
    public ResponseEntity<Resource> arquivo(
            @RequestParam("o") String objeto,
            @RequestParam("e") long expiraEm,
            @RequestParam("s") String assinatura) {

        if (!storage.assinaturaValida(objeto, expiraEm, assinatura, Instant.now())) {
            // Mesma resposta para assinatura inválida, caminho inválido ou link expirado.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Link de acesso expirado ou inválido.");
        }
        if (!storage.existe(objeto)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo do material não encontrado.");
        }

        String mime = objeto.toLowerCase(Locale.ROOT).endsWith(".pdf") ? TipoArquivo.PDF.mimeType()
                : objeto.toLowerCase(Locale.ROOT).endsWith(".webm") ? TipoArquivo.WEBM.mimeType()
                : TipoArquivo.MP4.mimeType();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(storage.abrir(objeto, 0));
    }
}
