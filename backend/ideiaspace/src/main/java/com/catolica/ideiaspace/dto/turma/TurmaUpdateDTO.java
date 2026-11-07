package com.catolica.ideiaspace.dto.turma;

import com.catolica.ideiaspace.domain.enums.StatusTurma;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TurmaUpdateDTO(
        @NotNull(message = "A disciplina vinculada é obrigatória.")
        Long disciplinaId,

        @NotBlank(message = "O código da turma é obrigatório.")
        @Size(max = 50, message = "O código da turma deve ter no máximo 50 caracteres.")
        String codigo,

        @NotBlank(message = "O nome da turma é obrigatório.")
        @Size(min = 2, max = 255, message = "O nome da turma deve ter entre 2 e 255 caracteres.")
        String nome,

        @NotNull(message = "O ano da turma é obrigatório.")
        @Min(value = 2000, message = "O ano da turma deve ser no mínimo 2000.")
        @Max(value = 2100, message = "O ano da turma deve ser no máximo 2100.")
        Integer ano,

        @NotNull(message = "O semestre da turma é obrigatório.")
        @Min(value = 1, message = "O semestre da turma deve ser 1 ou 2.")
        @Max(value = 2, message = "O semestre da turma deve ser 1 ou 2.")
        Integer semestre,

        StatusTurma status
) {}
