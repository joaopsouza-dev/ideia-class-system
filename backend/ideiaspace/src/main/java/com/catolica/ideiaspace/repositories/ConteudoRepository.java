package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.Conteudo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConteudoRepository extends JpaRepository<Conteudo, Long> {

    List<Conteudo> findByExcluidoEmIsNullOrderByCriadoEmDesc();

    List<Conteudo> findByTurmaIdAndExcluidoEmIsNullOrderByCriadoEmDesc(Long turmaId);

    Optional<Conteudo> findByIdAndExcluidoEmIsNull(Long id);

    /*
     * INSERT direto não é permitido ao role app_backend: a criação passa pela função
     * fn_criar_conteudo, que valida permissão, vínculo DOCENTE do autor e retorna o novo id.
     */
    @Query(value = "SELECT public.fn_criar_conteudo("
            + "CAST(:turmaId AS BIGINT), "
            + "CAST(:professorId AS BIGINT), "
            + "CAST(:titulo AS VARCHAR), "
            + "CAST(:descricao AS TEXT), "
            + "CAST(:tipoMaterial AS tipo_material_enum), "
            + "CAST(:storagePath AS TEXT), "
            + "CAST(:mimeType AS VARCHAR), "
            + "CAST(:tamanhoBytes AS BIGINT), "
            + "CAST(:sha256 AS VARCHAR))", nativeQuery = true)
    Long criarPorFuncao(
            @Param("turmaId") Long turmaId,
            @Param("professorId") Long professorId,
            @Param("titulo") String titulo,
            @Param("descricao") String descricao,
            @Param("tipoMaterial") String tipoMaterial,
            @Param("storagePath") String storagePath,
            @Param("mimeType") String mimeType,
            @Param("tamanhoBytes") Long tamanhoBytes,
            @Param("sha256") String sha256);

    /*
     * Soft delete: DELETE não é concedido ao app_backend, e a coluna excluido_em não tem UPDATE.
     * A função fn_arquivar_conteudo aplica a exclusão lógica. Retorna 1 quando executada;
     * exceções de permissão são propagadas pelo banco.
     */
    @Query(value = "SELECT COUNT(*) FROM (SELECT public.fn_arquivar_conteudo(CAST(:id AS BIGINT))) AS t",
            nativeQuery = true)
    Long arquivarPorFuncao(@Param("id") Long id);
}
