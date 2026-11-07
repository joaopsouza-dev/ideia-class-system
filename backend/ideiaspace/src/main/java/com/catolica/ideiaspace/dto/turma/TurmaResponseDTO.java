package com.catolica.ideiaspace.dto.turma;

import com.catolica.ideiaspace.domain.Turma;
import com.catolica.ideiaspace.domain.enums.StatusTurma;

import java.time.LocalDateTime;

public record TurmaResponseDTO(
        Long id,
        Long disciplinaId,
        String disciplinaNome,
        String codigo,
        String nome,
        Integer ano,
        Integer semestre,
        StatusTurma status,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static TurmaResponseDTO fromEntity(Turma turma) {
        if (turma == null) {
            return null;
        }
        return new TurmaResponseDTO(
                turma.getId(),
                turma.getDisciplina() != null ? turma.getDisciplina().getId() : null,
                turma.getDisciplina() != null ? turma.getDisciplina().getNome() : null,
                turma.getCodigo(),
                turma.getNome(),
                turma.getAno(),
                turma.getSemestre(),
                turma.getStatus(),
                turma.getCriadoEm(),
                turma.getAtualizadoEm()
        );
    }
}
