package com.catolica.ideiaspace.repositories;

import com.catolica.ideiaspace.model.LogAcesso;
import com.catolica.ideiaspace.model.LogAcessoId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAcessoRepository extends JpaRepository<LogAcesso, LogAcessoId> {
}
