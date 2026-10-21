package com.catolica.ideiaspace.service;
import com.catolica.ideiaspace.domain.Turma;
import com.catolica.ideiaspace.repositories.TurmaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;


@Service
public class TurmaService {

    @Autowired
    private final TurmaRepository turmaRepository;

    public TurmaService(TurmaRepository turmaRepository) {
        this.turmaRepository = turmaRepository;
    }

    @Transactional(readOnly = true)
    public List<Turma> listarTurmas() {
        return turmaRepository.findAll();
    }

    @Transactional
    public Turma cadastrarTurma(Turma turma) {
        turma.setId(null);
        return turmaRepository.save(turma);
    }

}
