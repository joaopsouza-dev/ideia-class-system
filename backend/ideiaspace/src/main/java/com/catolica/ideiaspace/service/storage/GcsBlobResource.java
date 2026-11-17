package com.catolica.ideiaspace.service.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.springframework.core.io.AbstractResource;

import java.io.InputStream;
import java.nio.channels.Channels;

/**
 * Leitura em streaming de um objeto do bucket, para o endpoint de leitura de quem GERENCIA o
 * material. O tamanho vem do banco (tamanho_bytes), o que evita ler o objeto inteiro só para
 * descobrir o Content-Length e permite que o Spring atenda requisições com Range.
 */
final class GcsBlobResource extends AbstractResource {

    private final Storage storage;
    private final BlobId blobId;
    private final long tamanhoBytes;

    GcsBlobResource(Storage storage, BlobId blobId, long tamanhoBytes) {
        this.storage = storage;
        this.blobId = blobId;
        this.tamanhoBytes = tamanhoBytes;
    }

    @Override
    public String getDescription() {
        // Sem o nome do objeto: é um detalhe interno do armazenamento.
        return "objeto privado do bucket";
    }

    @Override
    public InputStream getInputStream() {
        return Channels.newInputStream(storage.reader(blobId));
    }

    @Override
    public long contentLength() {
        return tamanhoBytes;
    }

    @Override
    public long lastModified() {
        return 0L;
    }

    @Override
    public boolean exists() {
        return true;
    }
}
