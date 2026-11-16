package com.catolica.ideiaspace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração do armazenamento privado de materiais (Etapa 3).
 *
 * O diretório raiz fica FORA de qualquer pasta servida estaticamente e só é lido pela
 * aplicação. Em Docker ele é um volume nomeado (ver compose.yaml), o que preserva os
 * arquivos entre reinicializações dos containers.
 */
@Component
public class StorageProperties {

    private static final long BYTES_POR_MB = 1024L * 1024L;

    @Value("${APP_STORAGE_ROOT:./data/storage}")
    private String root;

    @Value("${APP_UPLOAD_MAX_PDF_MB:50}")
    private long maxPdfMb;

    @Value("${APP_UPLOAD_MAX_VIDEO_MB:500}")
    private long maxVideoMb;

    public String getRoot() {
        return root;
    }

    public long getMaxPdfBytes() {
        return maxPdfMb * BYTES_POR_MB;
    }

    public long getMaxVideoBytes() {
        return maxVideoMb * BYTES_POR_MB;
    }

    public long getMaxPdfMb() {
        return maxPdfMb;
    }

    public long getMaxVideoMb() {
        return maxVideoMb;
    }
}
