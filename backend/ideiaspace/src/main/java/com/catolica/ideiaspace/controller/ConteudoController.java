package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.conteudo.ConteudoRequestDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoResponseDTO;
import com.catolica.ideiaspace.dto.conteudo.ConteudoUpdateDTO;
import com.catolica.ideiaspace.service.ConteudoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conteudos")
public class ConteudoController {

    @Autowired
    private final ConteudoService conteudoService;

    public ConteudoController(ConteudoService conteudoService) {
        this.conteudoService = conteudoService;
    }

    @GetMapping
    public ResponseEntity<List<ConteudoResponseDTO>> listar(
            @RequestParam(name = "turmaId", required = false) Long turmaId) {
        return ResponseEntity.ok(conteudoService.listar(turmaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConteudoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(conteudoService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<ConteudoResponseDTO> cadastrar(@Valid @RequestBody ConteudoRequestDTO requestDTO) {
        ConteudoResponseDTO criado = conteudoService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConteudoResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ConteudoUpdateDTO updateDTO) {
        return ResponseEntity.ok(conteudoService.atualizar(id, updateDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        conteudoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
