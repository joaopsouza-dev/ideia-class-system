package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.auditoria.AuditoriaResponseDTO;
import com.catolica.ideiaspace.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public ResponseEntity<List<AuditoriaResponseDTO>> listar() {
        return ResponseEntity.ok(auditoriaService.listar());
    }
}
