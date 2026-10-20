-- ============================================================================
-- BOOTSTRAP DE ROLES - PostgreSQL 18+
-- Execute uma única vez como administrador do PostgreSQL.
-- Não coloque senha neste arquivo. Use Secret Manager / ambiente / certificado
-- conforme a política de infraestrutura.
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_owner') THEN
        CREATE ROLE app_owner
            NOLOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION
            NOBYPASSRLS;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_backend') THEN
        CREATE ROLE app_backend
            LOGIN
            NOSUPERUSER
            NOCREATEDB
            NOCREATEROLE
            NOINHERIT
            NOREPLICATION
            NOBYPASSRLS;
    END IF;
END;
$$;

-- O schema deve pertencer à role de owner/migration, não ao runtime.
-- Execute este arquivo como DBA/superuser ou proprietário do database/schema.
ALTER SCHEMA public OWNER TO app_owner;

-- Garante que o owner tenha os privilégios esperados no schema.
GRANT USAGE, CREATE ON SCHEMA public TO app_owner;

-- Concede conexão apenas ao banco em que este bootstrap está sendo executado.
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_backend', current_database());
END;
$$;

-- A autenticação do app_backend deve ser configurada pelo DBA/infraestrutura.
--
-- Evite senha fixa em scripts versionados.
