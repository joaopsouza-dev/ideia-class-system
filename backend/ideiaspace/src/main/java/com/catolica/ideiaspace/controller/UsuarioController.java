package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.usuario.AlteracaoSenhaDTO;
import com.catolica.ideiaspace.dto.usuario.AlunoDisponivelResponseDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioAdminResponseDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioAtualizacaoDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioStatusDTO;
import com.catolica.ideiaspace.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioAdminResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @GetMapping("/alunos")
    @PreAuthorize("hasAnyRole('ADMIN','DOCENTE')")
    public ResponseEntity<List<AlunoDisponivelResponseDTO>> listarAlunos() {
        return ResponseEntity.ok(usuarioService.listarAlunosDisponiveis());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponseDTO> atualizarDados(
            @PathVariable Long id,
            @RequestBody UsuarioAtualizacaoDTO dto) {
        return ResponseEntity.ok(usuarioService.atualizarDados(id, dto));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioAdminResponseDTO> alterarStatus(
            @PathVariable Long id,
            @RequestBody UsuarioStatusDTO dto) {
        return ResponseEntity.ok(usuarioService.alterarStatus(id, dto));
    }

    @PatchMapping("/me/senha")
    public ResponseEntity<Void> alterarPropriaSenha(@RequestBody AlteracaoSenhaDTO dto) {
        usuarioService.alterarPropriaSenha(dto);
        return ResponseEntity.noContent().build();
    }
}
