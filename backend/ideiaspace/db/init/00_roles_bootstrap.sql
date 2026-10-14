-- =============================================================================
-- 00_roles_bootstrap.sql
-- Script de inicialização do banco de dados — executado AUTOMATICAMENTE
-- pelo container do PostgreSQL na PRIMEIRA vez que o volume é criado.
--
-- Por que este arquivo existe?
--   A migration V1 do Flyway (V1__firstversion.sql) verifica se as roles
--   "app_owner" e "app_backend" existem ANTES de criar qualquer tabela.
--   Se não existirem, ela lança uma exceção e a aplicação não sobe.
--   Este script cria essas roles com antecedência, antes do Flyway rodar.
--
-- Como o PostgreSQL executa este arquivo?
--   O container oficial do PostgreSQL executa automaticamente todos os
--   arquivos .sql e .sh presentes em /docker-entrypoint-initdb.d/
--   na PRIMEIRA inicialização (quando o volume está vazio).
--   Nas próximas inicializações, o script é ignorado (o volume já existe).
--
-- Este arquivo deve ser montado em:
--   /docker-entrypoint-initdb.d/00_roles_bootstrap.sql
--   (configurado no compose.yaml)
-- =============================================================================

-- Cria a role "app_owner" se ainda não existir.
-- app_owner é o proprietário das tabelas e dos objetos do banco.
-- Ela não é usada pela aplicação em tempo de execução — apenas nas migrations.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_owner') THEN
        CREATE ROLE app_owner
            NOLOGIN        -- Não faz login diretamente (é uma role de posse)
            NOSUPERUSER    -- Não é superusuário
            NOCREATEDB     -- Não cria novos bancos
            NOCREATEROLE;  -- Não cria novas roles
        RAISE NOTICE 'Role app_owner criada.';
    ELSE
        RAISE NOTICE 'Role app_owner já existe. Pulando.';
    END IF;
END;
$$;

-- Cria a role "app_backend" se ainda não existir.
-- app_backend é a role usada pela aplicação Spring Boot em tempo de execução.
-- Ela recebe apenas as permissões mínimas necessárias (princípio do menor privilégio).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_backend') THEN
        CREATE ROLE app_backend
            LOGIN                              -- Pode fazer login (é usada pela aplicação)
            NOSUPERUSER                        -- Não é superusuário
            NOCREATEDB                         -- Não cria bancos
            NOCREATEROLE                       -- Não cria roles
            NOINHERIT                          -- Não herda permissões automaticamente
            NOBYPASSRLS                        -- NÃO ignora Row Level Security (obrigatório!)
            PASSWORD 'app_backend_password';   -- Senha de desenvolvimento; troque em produção
        RAISE NOTICE 'Role app_backend criada.';
    ELSE
        RAISE NOTICE 'Role app_backend já existe. Pulando.';
    END IF;
END;
$$;

-- Transfere a posse do schema public para app_owner.
-- A V1 exige que "public" pertença a app_owner.
-- O superusuário (postgres) tem permissão para fazer isso.
ALTER SCHEMA public OWNER TO app_owner;

-- Concede a app_owner os privilégios necessários no schema.
GRANT USAGE, CREATE ON SCHEMA public TO app_owner;

RAISE NOTICE '=== Bootstrap de roles concluído com sucesso. ===';
