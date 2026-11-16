package com.catolica.ideiaspace.dto.usuario;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;

/** Campos nulos são mantidos. Apenas ADMIN pode informar {@code perfil}. */
public record UsuarioAtualizacaoDTO(
        String nomeCompleto,
        String email,
        PerfilUsuario perfil
) {
}
