package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByIdAndExcluidoEmIsNull(Long id);
}
