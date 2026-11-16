package com.catolica.ideiaspace.service.storage;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Identifica PDF, MP4 e WebM pela assinatura binária do início do arquivo.
 * Não executa o conteúdo nem o interpreta além do cabeçalho.
 */
public final class DetectorTipoArquivo {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** A especificação do PDF permite o cabeçalho nos primeiros 1024 bytes. */
    private static final int PDF_JANELA = 1024;

    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] WEBM_DOCTYPE = "webm".getBytes(StandardCharsets.US_ASCII);
    private static final int EBML_JANELA = 64;

    /** Marcas principais (major brand) de MP4 aceitas. Exclui HEIC, AVIF, M4A, 3GP e MOV. */
    private static final Set<String> MARCAS_MP4 = Set.of(
            "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
            "mp41", "mp42", "avc1", "dash", "mmp4", "M4V ", "M4VH", "M4VP", "f4v ");

    private DetectorTipoArquivo() {}

    public static Optional<TipoArquivo> detectar(byte[] cabecalho) {
        if (cabecalho == null || cabecalho.length == 0) {
            return Optional.empty();
        }
        if (indexOf(cabecalho, PDF_MAGIC, Math.min(cabecalho.length, PDF_JANELA)) >= 0) {
            return Optional.of(TipoArquivo.PDF);
        }
        if (isWebm(cabecalho)) {
            return Optional.of(TipoArquivo.WEBM);
        }
        if (isMp4(cabecalho)) {
            return Optional.of(TipoArquivo.MP4);
        }
        return Optional.empty();
    }

    private static boolean isWebm(byte[] b) {
        if (b.length < EBML_MAGIC.length + WEBM_DOCTYPE.length) {
            return false;
        }
        for (int i = 0; i < EBML_MAGIC.length; i++) {
            if (b[i] != EBML_MAGIC[i]) {
                return false;
            }
        }
        // DocType "webm" distingue WebM de outros contêineres Matroska (ex.: "matroska").
        return indexOf(b, WEBM_DOCTYPE, Math.min(b.length, EBML_JANELA)) >= 0;
    }

    private static boolean isMp4(byte[] b) {
        // Estrutura ISO BMFF: [tamanho (4 bytes)]["ftyp"][major brand (4 bytes)]...
        if (b.length < 12) {
            return false;
        }
        if (b[4] != 'f' || b[5] != 't' || b[6] != 'y' || b[7] != 'p') {
            return false;
        }
        String marca = new String(b, 8, 4, StandardCharsets.US_ASCII);
        return MARCAS_MP4.contains(marca);
    }

    private static int indexOf(byte[] dados, byte[] padrao, int limite) {
        int max = Math.min(limite, dados.length) - padrao.length;
        for (int i = 0; i <= max; i++) {
            boolean igual = true;
            for (int j = 0; j < padrao.length; j++) {
                if (dados[i + j] != padrao[j]) {
                    igual = false;
                    break;
                }
            }
            if (igual) {
                return i;
            }
        }
        return -1;
    }
}
