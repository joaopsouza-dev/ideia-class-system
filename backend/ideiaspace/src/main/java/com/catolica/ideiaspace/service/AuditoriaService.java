package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.dto.auditoria.AuditoriaResponseDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Leitura de log_acesso. A RLS limita o resultado: administrador vê todos os registros,
 * os demais usuários veem apenas os próprios.
 */
@Service
public class AuditoriaService {

    private static final int LIMITE = 500;

    private static final String SQL =
            "SELECT l.id, l.criado_em, l.usuario_id, u.nome_completo, u.perfil::text AS perfil, " +
            "       l.conteudo_id, c.titulo, l.acao::text AS acao, host(l.ip_origem) AS ip_origem, l.user_agent " +
            "  FROM log_acesso l " +
            "  JOIN usuario u ON u.id = l.usuario_id " +
            "  LEFT JOIN conteudo c ON c.id = l.conteudo_id " +
            " ORDER BY l.criado_em DESC, l.id DESC " +
            " LIMIT " + LIMITE;

    private final JdbcTemplate jdbcTemplate;

    public AuditoriaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<AuditoriaResponseDTO> listar() {
        return jdbcTemplate.query(SQL, (rs, rowNum) -> new AuditoriaResponseDTO(
                rs.getLong("id"),
                rs.getObject("criado_em", OffsetDateTime.class),
                rs.getLong("usuario_id"),
                rs.getString("nome_completo"),
                rs.getString("perfil"),
                rs.getObject("conteudo_id") == null ? null : rs.getLong("conteudo_id"),
                rs.getString("titulo"),
                rs.getString("acao"),
                rs.getString("ip_origem"),
                rs.getString("user_agent")));
    }
}
