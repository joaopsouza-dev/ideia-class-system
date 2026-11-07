package com.catolica.ideiaspace.dto.auth;

public record LoginRequestDTO(
    String email,
    String senha
) {
}
