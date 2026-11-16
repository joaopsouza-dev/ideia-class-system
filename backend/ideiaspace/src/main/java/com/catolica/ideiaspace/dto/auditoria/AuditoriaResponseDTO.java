package com.catolica.ideiaspace.dto.auditoria;

import java.time.OffsetDateTime;

/** Linha de log_acesso já com nomes para exibição. Somente registros reais do banco. */
public record AuditoriaResponseDTO(
        Long id,
        OffsetDateTime criadoEm,
        Long usuarioId,
        String usuarioNome,
        String perfilUsuario,
        Long conteudoId,
        String conteudoTitulo,
        String acao,
        String ipOrigem,
        String userAgent
) {
}
