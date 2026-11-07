package com.catolica.ideiaspace.controller;

import com.catolica.ideiaspace.dto.auth.LoginRequestDTO;
import com.catolica.ideiaspace.dto.auth.LoginResponseDTO;
import com.catolica.ideiaspace.dto.auth.ProvisionRequestDTO;
import com.catolica.ideiaspace.dto.auth.ProvisionResponseDTO;
import com.catolica.ideiaspace.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO requestDTO) {
        LoginResponseDTO response = authService.login(requestDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/provision")
    public ResponseEntity<ProvisionResponseDTO> provision(@RequestBody ProvisionRequestDTO requestDTO) {
        ProvisionResponseDTO response = authService.provisionar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
