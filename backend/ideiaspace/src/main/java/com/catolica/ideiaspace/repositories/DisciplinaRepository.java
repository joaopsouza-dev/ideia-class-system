package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisciplinaRepository extends JpaRepository<Disciplina, Long> {

    List<Disciplina> findByExcluidoEmIsNull();

    Optional<Disciplina> findByIdAndExcluidoEmIsNull(Long id);

    @Query("SELECT COUNT(d) > 0 FROM Disciplina d WHERE LOWER(TRIM(d.codigo)) = LOWER(TRIM(:codigo)) AND d.excluidoEm IS NULL")
    boolean existsByCodigoAtivo(@Param("codigo") String codigo);

    @Query("SELECT COUNT(d) > 0 FROM Disciplina d WHERE LOWER(TRIM(d.codigo)) = LOWER(TRIM(:codigo)) AND d.excluidoEm IS NULL AND d.id <> :id")
    boolean existsByCodigoAtivoAndIdNot(@Param("codigo") String codigo, @Param("id") Long id);

    @Query("SELECT COUNT(t) > 0 FROM Turma t WHERE t.disciplina.id = :disciplinaId AND t.excluidoEm IS NULL")
    boolean hasTurmasAtivas(@Param("disciplinaId") Long disciplinaId);
}
