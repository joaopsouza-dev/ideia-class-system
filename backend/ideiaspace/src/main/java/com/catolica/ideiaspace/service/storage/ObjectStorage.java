package com.catolica.ideiaspace.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;

/**
 * Armazenamento privado dos arquivos de material. O PostgreSQL guarda apenas o
 * {@code storagePath} devolvido por {@link #salvar}; nunca uma URL.
 *
 * Implementações: {@link FileStorageService} (disco local; padrão e testes) e
 * {@code FirebaseObjectStorage} (Firebase Storage / Google Cloud Storage).
 */
public interface ObjectStorage {

    /**
     * Valida (assinatura binária, tipo e tamanho) e grava o arquivo com nome gerado pelo servidor.
     * Lança ResponseStatusException 400/413/415 se inválido e 5xx se o armazenamento falhar.
     */
    ArquivoArmazenado salvar(MultipartFile arquivo);

    /** Verdadeiro se o objeto existe. Nunca lança para caminho inválido. */
    boolean existe(String storagePath);

    /** Remoção "melhor esforço": nunca lança; falhas são apenas registradas. */
    void remover(String storagePath);

    /**
     * Leitura do conteúdo pelo próprio backend (usada só por quem gerencia o material).
     * Lança IllegalArgumentException para caminho inválido.
     */
    Resource abrir(String storagePath, long tamanhoBytes);

    /**
     * URL temporária de leitura (inline) do objeto. O endereço retornado pode ser absoluto
     * (Signed URL V4 do GCS) ou relativo à API (provedor local).
     */
    UrlTemporaria gerarUrlLeitura(String storagePath, String mimeType, Duration validade);

    /** Nome do provedor, apenas para auditoria e diagnóstico ("local" ou "firebase"). */
    String provedor();

    record UrlTemporaria(String url, Instant expiraEm) {}
}
