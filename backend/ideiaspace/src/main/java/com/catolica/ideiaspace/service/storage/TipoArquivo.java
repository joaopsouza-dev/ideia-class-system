package com.catolica.ideiaspace.service.storage;

/**
 * Tipos de arquivo aceitos como material. O tipo é sempre deduzido do CONTEÚDO do arquivo
 * (assinatura binária), nunca do nome ou do Content-Type informados pelo navegador.
 */
public enum TipoArquivo {
    PDF("application/pdf", "pdf"),
    MP4("video/mp4", "mp4"),
    WEBM("video/webm", "webm");

    private final String mimeType;
    private final String extensao;

    TipoArquivo(String mimeType, String extensao) {
        this.mimeType = mimeType;
        this.extensao = extensao;
    }

    public String mimeType() {
        return mimeType;
    }

    public String extensao() {
        return extensao;
    }

    public boolean isPdf() {
        return this == PDF;
    }

    public boolean isVideo() {
        return this == MP4 || this == WEBM;
    }
}
