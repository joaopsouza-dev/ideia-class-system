package com.catolica.ideiaspace.dto.auth;

public record LoginResponseDTO(
    String token,
    String tipo,
    UsuarioDTO usuario
) {
}
