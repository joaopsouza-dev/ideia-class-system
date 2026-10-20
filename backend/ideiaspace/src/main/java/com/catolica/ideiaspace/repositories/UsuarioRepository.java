package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}
