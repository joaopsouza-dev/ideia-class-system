package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.domain.LogAcesso;
import com.catolica.ideiaspace.domain.LogAcessoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAcessoRepository extends JpaRepository<LogAcesso, LogAcessoId> {
}
