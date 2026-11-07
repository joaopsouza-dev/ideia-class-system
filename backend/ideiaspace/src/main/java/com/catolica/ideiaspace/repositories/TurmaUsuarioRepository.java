package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.TurmaUsuario;
import com.catolica.ideiaspace.domain.enums.StatusMatricula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TurmaUsuarioRepository extends JpaRepository<TurmaUsuario, Long> {

    List<TurmaUsuario> findByTurmaIdAndExcluidoEmIsNull(Long turmaId);

    Optional<TurmaUsuario> findByIdAndExcluidoEmIsNull(Long id);

    @Query("SELECT COUNT(tu) > 0 FROM TurmaUsuario tu WHERE tu.turma.id = :turmaId AND tu.usuario.id = :usuarioId AND tu.status = 'ATIVA' AND tu.excluidoEm IS NULL")
    boolean existsAtivoByTurmaIdAndUsuarioId(@Param("turmaId") Long turmaId, @Param("usuarioId") Long usuarioId);

    Optional<TurmaUsuario> findByTurmaIdAndUsuarioIdAndStatusAndExcluidoEmIsNull(Long turmaId, Long usuarioId, StatusMatricula status);
}
