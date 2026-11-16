package com.catolica.ideiaspace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica a requisição a partir do JWT e revalida a conta no banco a cada chamada.
 * Assim, uma conta desativada ou com perfil alterado perde o acesso mesmo com um token ainda não expirado.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final TokenService tokenService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(TokenService tokenService, JdbcTemplate jdbcTemplate) {
        this.tokenService = tokenService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            TokenService.TokenClaims claims = tokenService.validate(token);

            if (claims != null && claims.usuarioId() != null) {
                autenticarSeContaAtiva(request, claims.usuarioId());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void autenticarSeContaAtiva(HttpServletRequest request, Long usuarioId) {
        StatusSessao status;
        try {
            status = carregarStatus(usuarioId);
        } catch (DataAccessException e) {
            // Fail closed: sem confirmação no banco, a requisição segue como anônima.
            log.warn("Não foi possível validar a sessão do usuário {}: {}", usuarioId, e.getMostSpecificCause().getMessage());
            return;
        }
        if (status == null || !"ATIVA".equals(status.statusConta())) {
            return;
        }

        UserPrincipal principal = new UserPrincipal(usuarioId, status.perfil());
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + status.perfil()));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserContext.setCurrentUserId(usuarioId);
    }

    private StatusSessao carregarStatus(Long usuarioId) {
        List<StatusSessao> resultado = jdbcTemplate.query(
                "SELECT perfil::text AS perfil, status_conta::text AS status_conta FROM fn_obter_status_sessao(?)",
                (rs, rowNum) -> new StatusSessao(rs.getString("perfil"), rs.getString("status_conta")),
                usuarioId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    private record StatusSessao(String perfil, String statusConta) {
    }
}
