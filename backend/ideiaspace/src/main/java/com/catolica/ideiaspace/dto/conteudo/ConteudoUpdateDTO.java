package com.catolica.ideiaspace.dto.conteudo;

import com.catolica.ideiaspace.domain.enums.TipoMaterial;
import jakarta.validation.constraints.*;

/*
 * turmaId e professorId não fazem parte da atualização: o banco não concede UPDATE sobre essas colunas.
 */
public record ConteudoUpdateDTO(
        @NotBlank(message = "O título do conteúdo é obrigatório.")
        @Size(min = 2, max = 255, message = "O título do conteúdo deve ter entre 2 e 255 caracteres.")
        String titulo,

        String descricao,

        @NotNull(message = "O tipo de material é obrigatório.")
        TipoMaterial tipoMaterial,

        @NotBlank(message = "O caminho de armazenamento é obrigatório.")
        @Size(max = 2048, message = "O caminho de armazenamento deve ter no máximo 2048 caracteres.")
        String storagePath,

        @NotBlank(message = "O tipo MIME é obrigatório.")
        @Size(max = 150, message = "O tipo MIME deve ter no máximo 150 caracteres.")
        String mimeType,

        @NotNull(message = "O tamanho do arquivo é obrigatório.")
        @PositiveOrZero(message = "O tamanho do arquivo não pode ser negativo.")
        Long tamanhoBytes,

        @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "O SHA-256 deve conter 64 caracteres hexadecimais.")
        String sha256
) {}
