package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.dto.usuario.UsuarioAtualizacaoDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioStatusDTO;
import com.catolica.ideiaspace.security.PasswordHasher;
import com.catolica.ideiaspace.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regra do último administrador ativo, exercitada no UsuarioService contra um JdbcTemplate falso
 * que mantém o estado das contas em memória (sem PostgreSQL).
 *
 * Observação: com a autorização revalidando a conta a cada requisição, um administrador ativo sempre
 * conta como "outro administrador" ao agir sobre terceiros. A guarda do último administrador cobre a
 * corrida em que o outro administrador é desativado entre a checagem e a alteração; os cenários abaixo
 * reproduzem esse estado com o ator já desativado.
 */
class UsuarioServiceUltimoAdministradorTest {

    private FakeJdbc jdbc;
    private UsuarioService service;

    @BeforeEach
    void setup() {
        jdbc = new FakeJdbc();
        service = new UsuarioService(jdbc, new PasswordHasher());
    }

    @AfterEach
    void limpar() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void atuandoComo(long id) {
        UserContext.setCurrentUserId(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ator", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void adicionar(long id, String email, String perfil, String status) {
        jdbc.usuarios.put(id, new Usuario(id, "Usuario " + id, email, perfil, status));
    }

    @Test
    @DisplayName("Desativa um administrador quando ainda há outro administrador ativo")
    void desativaAdministradorQuandoHaOutroAtivo() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "admin2@ideiaspace.com", "ADMIN", "ATIVA");
        atuandoComo(1L);

        service.alterarStatus(2L, new UsuarioStatusDTO(false));

        assertEquals("INATIVA", jdbc.usuarios.get(2L).status);
    }

    @Test
    @DisplayName("Recusa desativar o último administrador ativo (409)")
    void recusaDesativarUltimoAdministrador() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "admin2@ideiaspace.com", "ADMIN", "INATIVA");
        atuandoComo(2L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.alterarStatus(1L, new UsuarioStatusDTO(false)));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("ATIVA", jdbc.usuarios.get(1L).status);
    }

    @Test
    @DisplayName("Recusa rebaixar o último administrador ativo (409)")
    void recusaRebaixarUltimoAdministrador() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "admin2@ideiaspace.com", "ADMIN", "INATIVA");
        atuandoComo(2L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.atualizarDados(1L, new UsuarioAtualizacaoDTO(null, null, PerfilUsuario.DOCENTE)));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("ADMIN", jdbc.usuarios.get(1L).perfil);
    }

    @Test
    @DisplayName("Permite rebaixar um administrador quando há outro ativo")
    void permiteRebaixarAdministradorQuandoHaOutroAtivo() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "admin2@ideiaspace.com", "ADMIN", "ATIVA");
        atuandoComo(1L);

        service.atualizarDados(2L, new UsuarioAtualizacaoDTO(null, null, PerfilUsuario.DOCENTE));

        assertEquals("DOCENTE", jdbc.usuarios.get(2L).perfil);
    }

    @Test
    @DisplayName("Administrador não desativa a própria conta (422)")
    void naoDesativaProprioAdministrador() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "admin2@ideiaspace.com", "ADMIN", "ATIVA");
        atuandoComo(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.alterarStatus(1L, new UsuarioStatusDTO(false)));

        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("E-mail já usado por outra conta retorna 409 com mensagem específica")
    void emailDuplicadoRetornaConflito() {
        adicionar(1L, "admin1@ideiaspace.com", "ADMIN", "ATIVA");
        adicionar(2L, "aluno@ideiaspace.com", "ALUNO", "ATIVA");
        atuandoComo(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.atualizarDados(2L, new UsuarioAtualizacaoDTO(null, "admin1@ideiaspace.com", null)));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("E-mail já cadastrado.", ex.getReason());
    }

    // -----------------------------------------------------------------------
    // Estado simulado das contas e JdbcTemplate falso
    // -----------------------------------------------------------------------

    private static final class Usuario {
        final long id;
        String nome;
        String email;
        String perfil;
        String status;

        Usuario(long id, String nome, String email, String perfil, String status) {
            this.id = id;
            this.nome = nome;
            this.email = email;
            this.perfil = perfil;
            this.status = status;
        }
    }

    private static final class FakeJdbc extends JdbcTemplate {
        final Map<Long, Usuario> usuarios = new HashMap<>();

        @Override
        public void execute(String sql) {
            // Advisory lock: no modelo em memória não há concorrência.
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE usuario SET nome_completo")) {
                Usuario u = usuarios.get((Long) args[2]);
                u.nome = (String) args[0];
                u.email = (String) args[1];
                return 1;
            }
            return 0;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> linhas = new ArrayList<>();
            if (sql.contains("SELECT nome_completo, email, perfil::text") || sql.contains("SELECT id, nome_completo")) {
                Usuario u = usuarios.get((Long) args[0]);
                if (u != null) {
                    try {
                        linhas.add(rowMapper.mapRow(rowSet(u), 0));
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return linhas;
        }

        @Override
        public void query(String sql, RowCallbackHandler handler, Object... args) {
            if (sql.contains("fn_atualizar_perfil_status_usuario")) {
                Usuario u = usuarios.get((Long) args[0]);
                u.perfil = (String) args[1];
                u.status = (String) args[2];
            }
            // Demais consultas (vínculos com turmas) não afetam estas regras.
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> tipo, Object... args) {
            long total = 0;
            if (sql.contains("lower(email)")) {
                String email = (String) args[0];
                long ignorada = (Long) args[1];
                for (Usuario u : usuarios.values()) {
                    if (u.id != ignorada && u.email.equalsIgnoreCase(email)) total++;
                }
            } else if (sql.contains("perfil = 'ADMIN'")) {
                long ignorada = (Long) args[0];
                for (Usuario u : usuarios.values()) {
                    if (u.id != ignorada && "ADMIN".equals(u.perfil) && "ATIVA".equals(u.status)) total++;
                }
            }
            return tipo.cast(total);
        }

        private static ResultSet rowSet(Usuario u) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, metodo, args) -> {
                        switch (metodo.getName()) {
                            case "getLong":
                                return u.id;
                            case "getString":
                                switch ((String) args[0]) {
                                    case "nome_completo": return u.nome;
                                    case "email": return u.email;
                                    case "perfil": return u.perfil;
                                    case "status_conta": return u.status;
                                    default: return null;
                                }
                            case "getObject":
                                return OffsetDateTime.now();
                            default:
                                throw new UnsupportedOperationException(metodo.getName());
                        }
                    });
        }
    }
}
