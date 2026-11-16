package com.catolica.ideiaspace.service.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários (sem Spring e sem banco) da detecção de tipo por assinatura. */
class DetectorTipoArquivoTest {

    static byte[] mp4(String marca) {
        byte[] b = new byte[32];
        b[3] = 0x20;
        b[4] = 'f'; b[5] = 't'; b[6] = 'y'; b[7] = 'p';
        byte[] m = marca.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(m, 0, b, 8, 4);
        return b;
    }

    static byte[] webm(String docType) {
        byte[] head = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x82, (byte) 0x84};
        byte[] dt = docType.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + dt.length + 8];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(dt, 0, out, head.length, dt.length);
        return out;
    }

    @Test
    @DisplayName("Reconhece PDF pelo cabeçalho %PDF-")
    void pdf() {
        byte[] b = "%PDF-1.7\nconteudo".getBytes(StandardCharsets.US_ASCII);
        assertEquals(Optional.of(TipoArquivo.PDF), DetectorTipoArquivo.detectar(b));
    }

    @Test
    @DisplayName("Aceita cabeçalho PDF deslocado dentro da janela de 1024 bytes")
    void pdfComLixoNoInicio() {
        byte[] b = new byte[200];
        byte[] m = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(m, 0, b, 100, m.length);
        assertEquals(Optional.of(TipoArquivo.PDF), DetectorTipoArquivo.detectar(b));
    }

    @Test
    @DisplayName("Reconhece MP4 (ftyp isom/mp42) e rejeita HEIC e MOV")
    void mp4() {
        assertEquals(Optional.of(TipoArquivo.MP4), DetectorTipoArquivo.detectar(mp4("isom")));
        assertEquals(Optional.of(TipoArquivo.MP4), DetectorTipoArquivo.detectar(mp4("mp42")));
        assertTrue(DetectorTipoArquivo.detectar(mp4("heic")).isEmpty());
        assertTrue(DetectorTipoArquivo.detectar(mp4("qt  ")).isEmpty());
    }

    @Test
    @DisplayName("Reconhece WebM e rejeita outros contêineres Matroska")
    void webm() {
        assertEquals(Optional.of(TipoArquivo.WEBM), DetectorTipoArquivo.detectar(webm("webm")));
        assertTrue(DetectorTipoArquivo.detectar(webm("matroska")).isEmpty());
    }

    @Test
    @DisplayName("Rejeita texto, HTML, executáveis, vazio e nulo mesmo com extensão enganosa")
    void invalidos() {
        assertTrue(DetectorTipoArquivo.detectar("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(DetectorTipoArquivo.detectar("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)).isEmpty());
        assertTrue(DetectorTipoArquivo.detectar("apenas texto qualquer".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(DetectorTipoArquivo.detectar(new byte[0]).isEmpty());
        assertTrue(DetectorTipoArquivo.detectar(null).isEmpty());
    }

    @Test
    @DisplayName("PDF não é confundido com vídeo e vice-versa")
    void tiposExclusivos() {
        assertTrue(TipoArquivo.PDF.isPdf() && !TipoArquivo.PDF.isVideo());
        assertTrue(TipoArquivo.MP4.isVideo() && !TipoArquivo.MP4.isPdf());
        assertTrue(TipoArquivo.WEBM.isVideo() && !TipoArquivo.WEBM.isPdf());
    }
}
