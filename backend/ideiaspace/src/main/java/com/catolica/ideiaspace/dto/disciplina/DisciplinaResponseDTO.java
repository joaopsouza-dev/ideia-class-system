package com.catolica.ideiaspace.dto.disciplina;

import com.catolica.ideiaspace.domain.Disciplina;

import java.time.LocalDateTime;

public record DisciplinaResponseDTO(
    Long id,
    String codigo,
    String nome,
    String descricao,
    LocalDateTime criadoEm,
    LocalDateTime atualizadoEm
) {
    public static DisciplinaResponseDTO fromEntity(Disciplina disciplina) {
        if (disciplina == null) {
            return null;
        }
        return new DisciplinaResponseDTO(
            disciplina.getId(),
            disciplina.getCodigo(),
            disciplina.getNome(),
            disciplina.getDescricao(),
            disciplina.getCriadoEm(),
            disciplina.getAtualizadoEm()
        );
    }
}
