package com.catolica.ideiaspace.dto.turma;

import com.catolica.ideiaspace.domain.enums.PapelTurma;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TurmaUsuarioRequestDTO(
        @NotNull(message = "O ID do usuário é obrigatório.")
        Long usuarioId,

        @NotNull(message = "O papel do usuário na turma é obrigatório.")
        PapelTurma papel,

        LocalDate inicioEm,

        LocalDate fimEm
) {}
