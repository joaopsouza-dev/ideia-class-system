package com.catolica.ideiaspace.dto.turma;

import com.catolica.ideiaspace.domain.TurmaUsuario;
import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.StatusMatricula;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TurmaUsuarioResponseDTO(
        Long id,
        Long turmaId,
        Long usuarioId,
        String usuarioNome,
        String usuarioEmail,
        PapelTurma papel,
        StatusMatricula status,
        LocalDate inicioEm,
        LocalDate fimEm,
        LocalDateTime criadoEm
) {
    public static TurmaUsuarioResponseDTO fromEntity(TurmaUsuario tu) {
        if (tu == null) {
            return null;
        }
        return new TurmaUsuarioResponseDTO(
                tu.getId(),
                tu.getTurma() != null ? tu.getTurma().getId() : null,
                tu.getUsuario() != null ? tu.getUsuario().getId() : null,
                tu.getUsuario() != null ? tu.getUsuario().getNomeCompleto() : null,
                tu.getUsuario() != null ? tu.getUsuario().getEmail() : null,
                tu.getPapel(),
                tu.getStatus(),
                tu.getInicioEm(),
                tu.getFimEm(),
                tu.getCriadoEm()
        );
    }
}
