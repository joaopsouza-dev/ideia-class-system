package com.catolica.ideiaspace.dto.auth;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;

public record ProvisionRequestDTO(
    String nomeCompleto,
    String email,
    String senha,
    PerfilUsuario perfil
) {
}
