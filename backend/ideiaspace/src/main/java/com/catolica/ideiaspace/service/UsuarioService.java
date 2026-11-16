package com.catolica.ideiaspace.service;

import com.catolica.ideiaspace.domain.enums.PapelTurma;
import com.catolica.ideiaspace.domain.enums.PerfilUsuario;
import com.catolica.ideiaspace.domain.enums.StatusConta;
import com.catolica.ideiaspace.dto.usuario.AlteracaoSenhaDTO;
import com.catolica.ideiaspace.dto.usuario.AlunoDisponivelResponseDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioAdminResponseDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioAdminResponseDTO.TurmaVinculoDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioAtualizacaoDTO;
import com.catolica.ideiaspace.dto.usuario.UsuarioStatusDTO;
import com.catolica.ideiaspace.security.PasswordHasher;
import com.catolica.ideiaspace.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UsuarioService {

    private static final int SENHA_MINIMA = 8;
    private static final int NOME_MINIMO = 3;
    /** Chave do advisory lock que serializa alterações de administradores. */
    private static final long LOCK_ADMINISTRADORES = 724301L;

    private static final String SQL_USUARIOS =
            "SELECT id, nome_completo, email, perfil::text AS perfil, status_conta::text AS status_conta, criado_em " +
            "FROM usuario WHERE excluido_em IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;

    public UsuarioService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponseDTO> listar() {
        exigirAdmin();
        Map<Long, List<TurmaVinculoDTO>> vinculos = carregarVinculos(null);
        return jdbcTemplate.query(
                SQL_USUARIOS + " ORDER BY nome_completo, id",
                (rs, rowNum) -> mapear(rs, vinculos.getOrDefault(rs.getLong("id"), List.of())));
    }

    /** Alunos ativos disponíveis para matrícula. Autorização feita pela função do banco (ADMIN ou DOCENTE). */
    @Transactional(readOnly = true)
    public List<AlunoDisponivelResponseDTO> listarAlunosDisponiveis() {
        return jdbcTemplate.query(
                "SELECT out_id, out_nome_completo, out_email FROM fn_listar_alunos_disponiveis()",
                (rs, rowNum) -> new AlunoDisponivelResponseDTO(
                        rs.getLong("out_id"),
                        rs.getString("out_nome_completo"),
                        rs.getString("out_email")));
    }

    @Transactional
    public UsuarioAdminResponseDTO atualizarDados(Long alvoId, UsuarioAtualizacaoDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe os dados a alterar.");
        }
        boolean admin = ehAdmin();
        boolean proprio = usuarioAtualId().equals(alvoId);
        if (!admin && !proprio) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode alterar os seus próprios dados.");
        }
        if (!admin && dto.perfil() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas administradores podem alterar o perfil.");
        }
        if (admin) {
            bloquearAdministracao();
        }

        Estado atual = carregarEstado(alvoId);

        String nome = dto.nomeCompleto() != null ? dto.nomeCompleto().trim() : atual.nomeCompleto();
        String email = dto.email() != null ? dto.email().trim().toLowerCase() : atual.email();
        validarNome(nome);
        validarEmail(email);

        if (!email.equals(atual.email()) && emailEmUsoPorOutraConta(email, alvoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        }

        if (!nome.equals(atual.nomeCompleto()) || !email.equals(atual.email())) {
            jdbcTemplate.update(
                    "UPDATE usuario SET nome_completo = ?, email = ? WHERE id = ? AND excluido_em IS NULL",
                    nome, email, alvoId);
        }

        if (dto.perfil() != null && dto.perfil() != atual.perfil()) {
            if (proprio) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não é possível alterar o próprio perfil.");
            }
            if (atual.perfil() == PerfilUsuario.ADMIN && atual.status() == StatusConta.ATIVA) {
                garantirOutroAdministradorAtivo(alvoId);
            }
            chamarFuncao(
                    "SELECT fn_atualizar_perfil_status_usuario(?, ?::perfil_usuario_enum, ?::status_conta_enum)",
                    alvoId, dto.perfil().name(), atual.status().name());
        }

        return buscar(alvoId);
    }

    @Transactional
    public UsuarioAdminResponseDTO alterarStatus(Long alvoId, UsuarioStatusDTO dto) {
        exigirAdmin();
        if (dto == null || dto.ativo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o campo 'ativo'.");
        }
        bloquearAdministracao();

        boolean ativar = dto.ativo();
        if (!ativar && usuarioAtualId().equals(alvoId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Você não pode desativar a própria conta.");
        }

        Estado atual = carregarEstado(alvoId);
        StatusConta novo = ativar ? StatusConta.ATIVA : StatusConta.INATIVA;

        if (!ativar && atual.perfil() == PerfilUsuario.ADMIN && atual.status() == StatusConta.ATIVA) {
            garantirOutroAdministradorAtivo(alvoId);
        }
        if (novo != atual.status()) {
            chamarFuncao(
                    "SELECT fn_atualizar_perfil_status_usuario(?, ?::perfil_usuario_enum, ?::status_conta_enum)",
                    alvoId, atual.perfil().name(), novo.name());
        }

        return buscar(alvoId);
    }

    @Transactional
    public void alterarPropriaSenha(AlteracaoSenhaDTO dto) {
        Long atorId = usuarioAtualId();
        if (dto == null || estaVazio(dto.senhaAtual()) || estaVazio(dto.novaSenha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha atual e a nova senha.");
        }
        if (dto.novaSenha().length() < SENHA_MINIMA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ter ao menos 8 caracteres.");
        }
        if (dto.novaSenha().equals(dto.senhaAtual())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A nova senha deve ser diferente da senha atual.");
        }

        Estado atual = carregarEstado(atorId);
        // Hash obtido pela função existente; a coluna hash_senha não é legível pelo app_backend.
        String hashAtual = jdbcTemplate.queryForObject(
                "SELECT fn_obter_hash_senha_por_email(?)", String.class, atual.email());
        // 400 (e não 401): o frontend trata 401 como sessão expirada.
        if (hashAtual == null || !passwordHasher.verify(dto.senhaAtual(), hashAtual)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
        }

        chamarFuncao("SELECT fn_atualizar_senha(?, ?)", atorId, passwordHasher.hash(dto.novaSenha()));
    }

    private UsuarioAdminResponseDTO buscar(Long id) {
        Map<Long, List<TurmaVinculoDTO>> vinculos = carregarVinculos(id);
        List<UsuarioAdminResponseDTO> encontrados = jdbcTemplate.query(
                SQL_USUARIOS + " AND id = ?",
                (rs, rowNum) -> mapear(rs, vinculos.getOrDefault(id, List.of())),
                id);
        if (encontrados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        return encontrados.get(0);
    }

    private UsuarioAdminResponseDTO mapear(java.sql.ResultSet rs, List<TurmaVinculoDTO> turmas) throws java.sql.SQLException {
        return new UsuarioAdminResponseDTO(
                rs.getLong("id"),
                rs.getString("nome_completo"),
                rs.getString("email"),
                PerfilUsuario.valueOf(rs.getString("perfil")),
                StatusConta.valueOf(rs.getString("status_conta")),
                rs.getObject("criado_em", OffsetDateTime.class),
                turmas);
    }

    /** Vínculos ativos agrupados por usuário; {@code usuarioId} nulo carrega todos. */
    private Map<Long, List<TurmaVinculoDTO>> carregarVinculos(Long usuarioId) {
        Map<Long, List<TurmaVinculoDTO>> mapa = new HashMap<>();
        RowCallbackHandler handler = rs -> mapa
                .computeIfAbsent(rs.getLong("usuario_id"), k -> new ArrayList<>())
                .add(new TurmaVinculoDTO(
                        rs.getLong("turma_id"),
                        rs.getString("turma_nome"),
                        PapelTurma.valueOf(rs.getString("papel"))));
        String sql =
                "SELECT tu.usuario_id, t.id AS turma_id, t.nome AS turma_nome, tu.papel::text AS papel " +
                "FROM turma_usuario tu JOIN turma t ON t.id = tu.turma_id " +
                "WHERE tu.excluido_em IS NULL AND tu.status = 'ATIVA' AND t.excluido_em IS NULL";
        if (usuarioId == null) {
            jdbcTemplate.query(sql + " ORDER BY t.nome, t.id", handler);
        } else {
            jdbcTemplate.query(sql + " AND tu.usuario_id = ? ORDER BY t.nome, t.id", handler, usuarioId);
        }
        return mapa;
    }

    private Estado carregarEstado(Long id) {
        List<Estado> estados = jdbcTemplate.query(
                "SELECT nome_completo, email, perfil::text AS perfil, status_conta::text AS status_conta " +
                "FROM usuario WHERE id = ? AND excluido_em IS NULL",
                (rs, rowNum) -> new Estado(
                        rs.getString("nome_completo"),
                        rs.getString("email"),
                        PerfilUsuario.valueOf(rs.getString("perfil")),
                        StatusConta.valueOf(rs.getString("status_conta"))),
                id);
        if (estados.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        }
        return estados.get(0);
    }

    /**
     * Serializa alterações de administradores (advisory lock de transação), evitando que duas
     * operações concorrentes desativem ou rebaixem os últimos administradores ao mesmo tempo.
     */
    private void bloquearAdministracao() {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + LOCK_ADMINISTRADORES + ")");
    }

    /** Para não administradores o RLS oculta outras contas; nesse caso a constraint única do banco é a barreira final. */
    private boolean emailEmUsoPorOutraConta(String email, Long alvoId) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario WHERE lower(email) = ? AND id <> ? AND excluido_em IS NULL",
                Long.class, email, alvoId);
        return total != null && total > 0;
    }

    private void garantirOutroAdministradorAtivo(Long alvoId) {
        bloquearAdministracao();
        Long outros = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario " +
                "WHERE perfil = 'ADMIN' AND status_conta = 'ATIVA' AND excluido_em IS NULL AND id <> ?",
                Long.class, alvoId);
        if (outros == null || outros == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não é possível remover ou desativar o último administrador ativo.");
        }
    }

    /** Funções que retornam void: o resultado é ignorado; erros de permissão chegam como SQLState 42501. */
    private void chamarFuncao(String sql, Object... args) {
        RowCallbackHandler ignorar = rs -> { };
        jdbcTemplate.query(sql, ignorar, args);
    }

    private Long usuarioAtualId() {
        Long id = UserContext.getCurrentUserId();
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida.");
        }
        return id;
    }

    private boolean ehAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private void exigirAdmin() {
        if (!ehAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a administradores.");
        }
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.length() < NOME_MINIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome deve ter ao menos 3 caracteres.");
        }
    }

    private static void validarEmail(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail inválido.");
        }
    }

    private static boolean estaVazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private record Estado(String nomeCompleto, String email, PerfilUsuario perfil, StatusConta status) {
    }
}
