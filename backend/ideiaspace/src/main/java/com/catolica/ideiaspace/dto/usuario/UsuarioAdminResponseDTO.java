package com.catolica.ideiaspace.dto.usuario;

import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.domain.enums.StatusConta;

import java.time.OffsetDateTime;
import java.util.List;

public record UsuarioAdminResponseDTO(
        Long id,
        String nomeCompleto,
        String email,
        PerfilUsuario perfil,
        StatusConta statusConta,
        OffsetDateTime criadoEm,
        List<TurmaVinculoDTO> turmas
) {
    public record TurmaVinculoDTO(Long turmaId, String turmaNome, PapelTurma papel) {
    }
}
