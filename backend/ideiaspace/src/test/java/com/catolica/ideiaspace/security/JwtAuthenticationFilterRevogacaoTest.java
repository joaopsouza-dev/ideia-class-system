package com.catolica.ideiaspace.security;

import com.catolica.ideiaspace.config.AppSecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Revogação de sessão: o filtro JWT revalida o status e o perfil no banco a cada requisição.
 * Executa com um JdbcTemplate falso que simula a função fn_obter_status_sessao.
 */
class JwtAuthenticationFilterRevogacaoTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-123";

    private TokenService tokenService;
    private FakeJdbc jdbc;
    private JwtAuthenticationFilter filtro;

    @BeforeEach
    void setup() throws Exception {
        AppSecurityProperties props = new AppSecurityProperties();
        Field campo = AppSecurityProperties.class.getDeclaredField("jwtSecret");
        campo.setAccessible(true);
        campo.set(props, SEGREDO);
        tokenService = new TokenService(props);
        jdbc = new FakeJdbc();
        filtro = new JwtAuthenticationFilter(tokenService, jdbc);
    }

    @AfterEach
    void limpar() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** Executa o filtro e devolve a autenticação e o id de usuário vistos pela cadeia seguinte. */
    private Resultado executar(String token) throws Exception {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, metodo, args) -> "getHeader".equals(metodo.getName()) && token != null
                        ? "Bearer " + token : null);
        Resultado r = new Resultado();
        filtro.doFilterInternal(request, null, (req, res) -> {
            r.autenticacao = SecurityContextHolder.getContext().getAuthentication();
            r.usuarioId = UserContext.getCurrentUserId();
        });
        return r;
    }

    private static final class Resultado {
        Authentication autenticacao;
        Long usuarioId;

        String roleAtual() {
            if (autenticacao == null) return null;
            return autenticacao.getAuthorities().iterator().next().getAuthority();
        }
    }

    @Test
    @DisplayName("Conta ativa autentica com o perfil atual do banco")
    void contaAtivaAutentica() throws Exception {
        jdbc.contas.put(7L, new String[]{"ADMIN", "ATIVA"});

        Resultado r = executar(tokenService.generate(7L, "ADMIN"));

        assertEquals("ROLE_ADMIN", r.roleAtual());
        assertEquals(7L, r.usuarioId.longValue());
    }

    @Test
    @DisplayName("Token de conta desativada deixa de autenticar mesmo sem expirar")
    void contaDesativadaNaoAutentica() throws Exception {
        jdbc.contas.put(7L, new String[]{"ALUNO", "INATIVA"});

        Resultado r = executar(tokenService.generate(7L, "ALUNO"));

        assertNull(r.autenticacao);
        assertNull(r.usuarioId);
    }

    @Test
    @DisplayName("Conta bloqueada não autentica")
    void contaBloqueadaNaoAutentica() throws Exception {
        jdbc.contas.put(7L, new String[]{"ALUNO", "BLOQUEADA"});

        Resultado r = executar(tokenService.generate(7L, "ALUNO"));

        assertNull(r.autenticacao);
    }

    @Test
    @DisplayName("Token de conta excluída não autentica")
    void contaExcluidaNaoAutentica() throws Exception {
        Resultado r = executar(tokenService.generate(99L, "ALUNO"));

        assertNull(r.autenticacao);
    }

    @Test
    @DisplayName("Alteração de perfil vale imediatamente: a claim antiga não concede o papel anterior")
    void perfilAlteradoPrevaleceSobreClaim() throws Exception {
        jdbc.contas.put(7L, new String[]{"DOCENTE", "ATIVA"});

        Resultado r = executar(tokenService.generate(7L, "ADMIN"));

        assertEquals("ROLE_DOCENTE", r.roleAtual());
    }

    @Test
    @DisplayName("Falha ao consultar o banco nega a sessão (fail closed)")
    void falhaNoBancoNegaSessao() throws Exception {
        jdbc.falhar = true;

        Resultado r = executar(tokenService.generate(7L, "ADMIN"));

        assertNull(r.autenticacao);
    }

    @Test
    @DisplayName("Token adulterado não autentica e não consulta o banco")
    void tokenAdulteradoNaoAutentica() throws Exception {
        jdbc.contas.put(7L, new String[]{"ADMIN", "ATIVA"});
        String valido = tokenService.generate(7L, "ADMIN");
        String adulterado = valido.substring(0, valido.indexOf('.') + 1) + "AAAA" + valido.substring(valido.indexOf('.') + 5);

        Resultado r = executar(adulterado);

        assertNull(r.autenticacao);
        assertEquals(0, jdbc.consultas);
    }

    private static final class FakeJdbc extends JdbcTemplate {
        final Map<Long, String[]> contas = new HashMap<>();
        boolean falhar;
        int consultas;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            consultas++;
            if (falhar) {
                throw new DataAccessException("indisponível") { };
            }
            List<T> linhas = new ArrayList<>();
            if (sql.contains("fn_obter_status_sessao")) {
                String[] conta = contas.get((Long) args[0]);
                if (conta != null) {
                    try {
                        linhas.add(rowMapper.mapRow(linha(conta), 0));
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return linhas;
        }

        private static java.sql.ResultSet linha(String[] conta) {
            return (java.sql.ResultSet) Proxy.newProxyInstance(
                    java.sql.ResultSet.class.getClassLoader(),
                    new Class<?>[]{java.sql.ResultSet.class},
                    (proxy, metodo, args) -> switch ((String) args[0]) {
                        case "perfil" -> conta[0];
                        case "status_conta" -> conta[1];
                        default -> null;
                    });
        }
    }
}
