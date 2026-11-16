package com.catolica.ideiaspace.dto.conteudo;

import com.catolica.ideiaspace.domain.enums.StatusPublicacao;
import jakarta.validation.constraints.NotNull;

public record ConteudoStatusDTO(
        @NotNull(message = "O status de publicação é obrigatório.")
        StatusPublicacao statusPublicacao
) {}
