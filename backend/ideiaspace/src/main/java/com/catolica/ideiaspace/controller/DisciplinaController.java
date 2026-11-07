package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.disciplina.DisciplinaRequestDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaResponseDTO;
import com.catolica.ideiaspace.dto.disciplina.DisciplinaUpdateDTO;
import com.catolica.ideiaspace.service.DisciplinaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/disciplinas")
public class DisciplinaController {

    @Autowired
    private final DisciplinaService disciplinaService;

    public DisciplinaController(DisciplinaService disciplinaService) {
        this.disciplinaService = disciplinaService;
    }

    @GetMapping
    public ResponseEntity<List<DisciplinaResponseDTO>> listar() {
        List<DisciplinaResponseDTO> disciplinas = disciplinaService.listar();
        return ResponseEntity.ok(disciplinas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisciplinaResponseDTO> buscarPorId(@PathVariable Long id) {
        DisciplinaResponseDTO disciplina = disciplinaService.buscarPorId(id);
        return ResponseEntity.ok(disciplina);
    }

    @PostMapping
    public ResponseEntity<DisciplinaResponseDTO> cadastrar(@RequestBody DisciplinaRequestDTO requestDTO) {
        DisciplinaResponseDTO cadastrada = disciplinaService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastrada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DisciplinaResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestBody DisciplinaUpdateDTO updateDTO) {
        DisciplinaResponseDTO atualizada = disciplinaService.atualizar(id, updateDTO);
        return ResponseEntity.ok(atualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        disciplinaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
