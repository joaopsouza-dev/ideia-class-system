package com.catolica.ideiaspace.dto.conteudo;

import com.catolica.ideiaspace.domain.Conteudo;
import com.catolica.ideiaspace.domain.enums.TipoMaterial;

import java.time.LocalDateTime;

/*
 * storagePath é deliberadamente omitido: é um detalhe interno de armazenamento.
 * Expô-lo permitiria acesso ao arquivo sem passar pela autorização da aplicação.
 */
public record ConteudoResponseDTO(
        Long id,
        Long turmaId,
        Long professorId,
        String professorNome,
        String titulo,
        String descricao,
        TipoMaterial tipoMaterial,
        String mimeType,
        Long tamanhoBytes,
        String sha256,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {
    public static ConteudoResponseDTO fromEntity(Conteudo conteudo) {
        if (conteudo == null) {
            return null;
        }
        return new ConteudoResponseDTO(
                conteudo.getId(),
                conteudo.getTurma() != null ? conteudo.getTurma().getId() : null,
                conteudo.getProfessor() != null ? conteudo.getProfessor().getId() : null,
                conteudo.getProfessor() != null ? conteudo.getProfessor().getNomeCompleto() : null,
                conteudo.getTitulo(),
                conteudo.getDescricao(),
                conteudo.getTipoMaterial(),
                conteudo.getMimeType(),
                conteudo.getTamanhoBytes(),
                conteudo.getSha256(),
                conteudo.getCriadoEm(),
                conteudo.getAtualizadoEm()
        );
    }
}
