
package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.domain.Turma;
import com.catolica.ideiaspace.service.TurmaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/turmas")
public class TurmaController {

    private final TurmaService turmaService;

    public TurmaController(TurmaService turmaService) {
        this.turmaService = turmaService;
    }

    @GetMapping("/get")
    public ResponseEntity<List<Turma>> listarTurmas() {
        return ResponseEntity.ok(turmaService.listarTurmas());
    }

    @PostMapping("/")
    public ResponseEntity<Turma> cadastrarTurma(@RequestBody Turma turma) {

        Turma turmaCadastrada = turmaService.cadastrarTurma(turma);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(turmaCadastrada);
    }
}
