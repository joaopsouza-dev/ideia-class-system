package com.catolica.ideiaspace.dto.conteudo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Evento reportado pelo navegador durante a visualização. IP e User-Agent NÃO fazem parte do
 * contrato: são sempre determinados pelo servidor.
 */
public record EventoAcessoRequestDTO(
        @NotNull(message = "A ação do evento é obrigatória.")
        AcaoEventoCliente acao,

        @Size(max = 200, message = "O detalhe deve ter no máximo 200 caracteres.")
        String detalhe
) {
}
