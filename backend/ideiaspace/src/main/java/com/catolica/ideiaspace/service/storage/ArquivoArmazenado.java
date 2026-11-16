package com.catolica.ideiaspace.service.storage;

/**
 * Resultado de uma gravação bem-sucedida. storagePath é RELATIVO à raiz do armazenamento
 * e nunca é devolvido pela API.
 */
public record ArquivoArmazenado(
        String storagePath,
        TipoArquivo tipo,
        String mimeType,
        long tamanhoBytes,
        String sha256
) {}
