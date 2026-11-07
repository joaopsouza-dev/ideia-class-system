package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.domain.enums.StatusConta;
import com.catolica.ideiaspace.dto.auth.*;
import com.catolica.ideiaspace.security.PasswordHasher;
import com.catolica.ideiaspace.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO dto) {
        if (dto == null || dto.email() == null || dto.email().isBlank() || dto.senha() == null || dto.senha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email e senha são obrigatórios.");
        }

        String sql = "SELECT id, nome_completo, email, perfil::text, status_conta::text, hash_senha " +
                     "FROM fn_obter_dados_login_por_email(?)";

        List<UserData> users = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new UserData(
                        rs.getLong("id"),
                        rs.getString("nome_completo"),
                        rs.getString("email"),
                        PerfilUsuario.valueOf(rs.getString("perfil")),
                        StatusConta.valueOf(rs.getString("status_conta")),
                        rs.getString("hash_senha")
                ),
                dto.email().trim()
        );

        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        UserData user = users.get(0);

        if (user.statusConta() != StatusConta.ATIVA) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Conta inativa ou bloqueada.");
        }

        if (!passwordHasher.verify(dto.senha(), user.hashSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        String token = tokenService.generate(user.id(), user.perfil().name());

        UsuarioDTO usuarioDTO = new UsuarioDTO(user.id(), user.nomeCompleto(), user.email(), user.perfil());
        return new LoginResponseDTO(token, "Bearer", usuarioDTO);
    }

    @Transactional
    public ProvisionResponseDTO provisionar(ProvisionRequestDTO dto) {
        if (dto == null || dto.email() == null || dto.email().isBlank() || dto.senha() == null || dto.senha().isBlank() || dto.nomeCompleto() == null || dto.nomeCompleto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome completo, email e senha são obrigatórios.");
        }

        PerfilUsuario perfil = dto.perfil() != null ? dto.perfil() : PerfilUsuario.ALUNO;
        String hash = passwordHasher.hash(dto.senha());

        byte[] cpfCifrado = null;
        byte[] cpfLookupHmac = null;

        if (perfil == PerfilUsuario.ALUNO) {
            cpfCifrado = ("ENC_" + dto.email()).getBytes(StandardCharsets.UTF_8);
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                cpfLookupHmac = md.digest(dto.email().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                cpfLookupHmac = new byte[]{0x01, 0x02, 0x03};
            }
        }

        String sql = "SELECT fn_provisionar_usuario_inicial(?, ?, ?, ?::perfil_usuario_enum, ?, ?)";
        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                dto.nomeCompleto().trim(),
                dto.email().trim().toLowerCase(),
                hash,
                perfil.name(),
                cpfCifrado,
                cpfLookupHmac
        );

        return new ProvisionResponseDTO(id, "Usuário " + perfil + " provisionado com sucesso.");
    }

    private record UserData(
            Long id,
            String nomeCompleto,
            String email,
            PerfilUsuario perfil,
            StatusConta statusConta,
            String hashSenha
    ) {}
}
