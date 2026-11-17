package com.catolica.ideiaspace.dto.conteudo;

import java.time.OffsetDateTime;

/**
 * Fonte de visualização de um material (compatível com ViewerSource do frontend).
 * "url" é TEMPORÁRIA: Signed URL V4 do bucket (ou URL assinada local em desenvolvimento) e
 * nunca é persistida. O caminho interno do objeto não é informado.
 */
public record AcessoConteudoResponseDTO(
        String url,
        OffsetDateTime expiraEm,
        String tipo,
        String mimeType
) {
}
