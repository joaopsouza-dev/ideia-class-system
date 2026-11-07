package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.turma.*;
import com.catolica.ideiaspace.service.TurmaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/turmas")
public class TurmaController {

    @Autowired
    private final TurmaService turmaService;

    public TurmaController(TurmaService turmaService) {
        this.turmaService = turmaService;
    }

    @GetMapping
    public ResponseEntity<List<TurmaResponseDTO>> listar() {
        List<TurmaResponseDTO> turmas = turmaService.listar();
        return ResponseEntity.ok(turmas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurmaResponseDTO> buscarPorId(@PathVariable Long id) {
        TurmaResponseDTO turma = turmaService.buscarPorId(id);
        return ResponseEntity.ok(turma);
    }

    @PostMapping
    public ResponseEntity<TurmaResponseDTO> cadastrar(@Valid @RequestBody TurmaRequestDTO requestDTO) {
        TurmaResponseDTO cadastrada = turmaService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastrada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TurmaResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TurmaUpdateDTO updateDTO) {
        TurmaResponseDTO atualizada = turmaService.atualizar(id, updateDTO);
        return ResponseEntity.ok(atualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        turmaService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{turmaId}/participantes")
    public ResponseEntity<TurmaUsuarioResponseDTO> vincularUsuario(
            @PathVariable Long turmaId,
            @Valid @RequestBody TurmaUsuarioRequestDTO requestDTO) {
        TurmaUsuarioResponseDTO participante = turmaService.vincularUsuario(turmaId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(participante);
    }

    @GetMapping("/{turmaId}/participantes")
    public ResponseEntity<List<TurmaUsuarioResponseDTO>> listarParticipantes(@PathVariable Long turmaId) {
        List<TurmaUsuarioResponseDTO> participantes = turmaService.listarParticipantes(turmaId);
        return ResponseEntity.ok(participantes);
    }

    @DeleteMapping("/{turmaId}/participantes/{vinculoId}")
    public ResponseEntity<Void> removerVinculoUsuario(
            @PathVariable Long turmaId,
            @PathVariable Long vinculoId) {
        turmaService.removerVinculoUsuario(turmaId, vinculoId);
        return ResponseEntity.noContent().build();
    }
}
