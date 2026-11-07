package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TurmaRepository extends JpaRepository<Turma, Long> {

    List<Turma> findByExcluidoEmIsNull();

    Optional<Turma> findByIdAndExcluidoEmIsNull(Long id);

    @Query("SELECT COUNT(t) > 0 FROM Turma t WHERE LOWER(TRIM(t.codigo)) = LOWER(TRIM(:codigo)) AND t.excluidoEm IS NULL")
    boolean existsByCodigoAtivo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(t) > 0 FROM Turma t WHERE LOWER(TRIM(t.codigo)) = LOWER(TRIM(:codigo)) AND t.excluidoEm IS NULL AND t.id <> :id")
    boolean existsByCodigoAtivoAndIdNot(@Param("codigo") String codigo, @Param("id") Long id);

    @Query("SELECT COUNT(c) > 0 FROM Conteudo c WHERE c.turma.id = :turmaId AND c.excluidoEm IS NULL")
    boolean hasConteudosAtivos(@Param("turmaId") Long turmaId);

    @Query("SELECT COUNT(tu) > 0 FROM TurmaUsuario tu WHERE tu.turma.id = :turmaId AND tu.status = 'ATIVA' AND tu.excluidoEm IS NULL")
    boolean hasUsuariosAtivos(@Param("turmaId") Long turmaId);
}
