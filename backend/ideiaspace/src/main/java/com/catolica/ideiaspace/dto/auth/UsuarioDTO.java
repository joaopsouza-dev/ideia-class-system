package com.catolica.ideiaspace.dto.auth;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;

public record UsuarioDTO(
    Long id,
    String nomeCompleto,
    String email,
    PerfilUsuario perfil
) {
}
