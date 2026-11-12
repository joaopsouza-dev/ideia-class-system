package com.catolica.ideiaspace.config;

import com.catolica.ideiaspace.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Bootstrap controlado do primeiro administrador (Fase 0).
 *
 * Não existe rota pública para isso. A conta só é criada na inicialização da aplicação,
 * quando as variáveis APP_BOOTSTRAP_ADMIN_NAME, APP_BOOTSTRAP_ADMIN_EMAIL e
 * APP_BOOTSTRAP_ADMIN_PASSWORD estiverem definidas E o banco ainda não possuir
 * administrador. A verificação de "já existe admin" é feita pela função
 * fn_provisionar_usuario_inicial no banco. O perfil é sempre ADMIN.
 *
 * Depois que o primeiro administrador existir, as variáveis podem (e devem) ser removidas.
 */
@Component
public class FirstAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminBootstrap.class);
    private static final int SENHA_MINIMA = 12;
    /** Trecho da mensagem lançada pela função do banco quando já existe administrador. */
    private static final String MENSAGEM_ADMIN_EXISTENTE = "já possui administrador";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final String nome;
    private final String email;
    private final String senha;

    public FirstAdminBootstrap(
            JdbcTemplate jdbcTemplate,
            PasswordHasher passwordHasher,
            @Value("${APP_BOOTSTRAP_ADMIN_NAME:}") String nome,
            @Value("${APP_BOOTSTRAP_ADMIN_EMAIL:}") String email,
            @Value("${APP_BOOTSTRAP_ADMIN_PASSWORD:}") String senha
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean algumaDefinida = StringUtils.hasText(nome) || StringUtils.hasText(email) || StringUtils.hasText(senha);
        if (!algumaDefinida) {
            log.info("Bootstrap do administrador inicial não configurado (APP_BOOTSTRAP_ADMIN_*).");
            return;
        }
        if (!StringUtils.hasText(nome) || !StringUtils.hasText(email) || !StringUtils.hasText(senha)) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_NAME, APP_BOOTSTRAP_ADMIN_EMAIL e APP_BOOTSTRAP_ADMIN_PASSWORD devem ser definidas juntas.");
        }
        if (senha.length() < SENHA_MINIMA) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_ADMIN_PASSWORD deve ter no mínimo " + SENHA_MINIMA + " caracteres.");
        }

        String hash = passwordHasher.hash(senha);
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT fn_provisionar_usuario_inicial(?, ?, ?, 'ADMIN'::perfil_usuario_enum, NULL::bytea, NULL::bytea)",
                    Long.class,
                    nome.trim(),
                    email.trim().toLowerCase(Locale.ROOT),
                    hash
            );
            log.warn("Administrador inicial provisionado (id={}). Remova APP_BOOTSTRAP_ADMIN_PASSWORD do ambiente.", id);
        } catch (DataAccessException e) {
            String causa = e.getMostSpecificCause().getMessage();
            if (causa != null && causa.contains(MENSAGEM_ADMIN_EXISTENTE)) {
                log.info("Bootstrap ignorado: o banco já possui administrador provisionado.");
                return;
            }
            throw e;
        }
    }
}
