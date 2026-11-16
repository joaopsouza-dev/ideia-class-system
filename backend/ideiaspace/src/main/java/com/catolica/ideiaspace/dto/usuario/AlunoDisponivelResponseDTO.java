package com.catolica.ideiaspace.dto.usuario;

/** Dados mínimos de um aluno ativo para matrícula (sem CPF, sem hash de senha). */
public record AlunoDisponivelResponseDTO(Long id, String nomeCompleto, String email) {
}
