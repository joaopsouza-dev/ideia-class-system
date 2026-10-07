-- ============================================================================
-- V2.1 - ARQUITETURA DDL SEGURA E DE PRODUÇÃO
-- PostgreSQL 18+
-- Sistema: Repositório Digital Protegido de Conteúdo Acadêmico
--
-- Objetivos:
--   * Least Privilege
--   * RLS para isolamento por turma
--   * Security Definer para operações sensíveis
--   * Auditoria append-only e particionada
--   * Soft delete / anonimização
--   * Armazenamento externo de mídias (somente storage_path no banco)
--   * Autoria de conteúdo protegida contra falsificação
--   * Suporte a matrícula ativa
--   * Modelo Disciplina -> Turma -> Conteúdo
--
-- PRÉ-REQUISITOS:
--   1. Criar as roles pelo arquivo 00_roles_bootstrap.sql.
--   2. Executar esta migration como app_owner (ou uma role membro de app_owner
--      que possa executar SET ROLE app_owner).
--   3. app_backend NÃO deve ser owner das tabelas, superuser nem BYPASSRLS.
--   4. A aplicação deve definir app.current_user_id usando SET LOCAL dentro de
--      uma transação por requisição, nunca com SET persistente em pool.
--   5. O hash da senha deve ser produzido pelo backend (preferencialmente
--      Argon2id ou bcrypt). Este banco armazena apenas o hash.
--   6. cpf_cifrado e cpf_lookup_hmac devem ser produzidos pela camada de
--      aplicação. A chave de criptografia/HMAC não deve ficar no código SQL.
-- ============================================================================

BEGIN;
SET LOCAL TIME ZONE 'UTC';

-- ----------------------------------------------------------------------------
-- 0. PRÉ-VALIDAÇÕES DE SEGURANÇA
-- ----------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'app_owner'
    ) THEN
        RAISE EXCEPTION 'Role app_owner não existe. Execute 00_roles_bootstrap.sql.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'app_backend'
    ) THEN
        RAISE EXCEPTION 'Role app_backend não existe. Execute 00_roles_bootstrap.sql.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_namespace n
         WHERE n.nspname = 'public'
           AND pg_get_userbyid(n.nspowner) = 'app_owner'
    ) THEN
        RAISE EXCEPTION 'O schema public deve pertencer a app_owner. Execute o bootstrap como DBA antes da migration.';
    END IF;
END;
$$;

SET ROLE app_owner;

DO $$
DECLARE
    v_super BOOLEAN;
    v_bypass BOOLEAN;
BEGIN
    IF current_user <> 'app_owner' THEN
        RAISE EXCEPTION 'A migration deve executar como app_owner. Current user: %', current_user;
    END IF;

    SELECT rolsuper, rolbypassrls
      INTO v_super, v_bypass
      FROM pg_roles
     WHERE rolname = 'app_backend';

    IF v_super THEN
        RAISE EXCEPTION 'app_backend não pode ser SUPERUSER.';
    END IF;

    IF v_bypass THEN
        RAISE EXCEPTION 'app_backend não pode possuir BYPASSRLS.';
    END IF;
END;
$$;

-- ----------------------------------------------------------------------------
-- 1. HARDENING DE SCHEMA E PRIVILÉGIOS PADRÃO
-- ----------------------------------------------------------------------------

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO app_owner;
GRANT USAGE ON SCHEMA public TO app_backend;

-- Evita exposição acidental em objetos futuros criados por app_owner.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TYPES FROM PUBLIC;

-- Mesmo usando objetos em public, o search_path das funções sensíveis será
-- explicitamente fixado e pg_temp ficará por último.
SET search_path = public, pg_catalog;

-- ----------------------------------------------------------------------------
-- 2. TIPOS CONTROLADOS
-- ----------------------------------------------------------------------------

CREATE TYPE perfil_usuario_enum AS ENUM ('ALUNO', 'DOCENTE', 'ADMIN');
CREATE TYPE status_conta_enum AS ENUM ('ATIVA', 'INATIVA', 'BLOQUEADA');
CREATE TYPE papel_turma_enum AS ENUM ('DISCENTE', 'DOCENTE');
CREATE TYPE status_turma_enum AS ENUM ('ATIVA', 'INATIVA', 'CONCLUIDA');
CREATE TYPE status_matricula_enum AS ENUM ('ATIVA', 'INATIVA', 'CONCLUIDA', 'CANCELADA');
CREATE TYPE tipo_material_enum AS ENUM ('PDF_LIVRO', 'PDF_APOSTILA', 'VIDEO_AULA');
CREATE TYPE acao_log_enum AS ENUM (
    'ABRIU_PDF',
    'INICIOU_VIDEO',
    'ASSISTIU_VIDEO',
    'TENTATIVA_DOWNLOAD',
    'TENTATIVA_PRINT_DETECTADA',
    'TENTATIVA_ACESSO_NEGADO',
    'GEROU_URL_TEMPORARIA'
);

-- ----------------------------------------------------------------------------
-- 3. FUNÇÃO BASE DE IDENTIDADE DA SESSÃO
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION app_current_user_id()
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT CASE
        WHEN current_setting('app.current_user_id', true) ~ '^[1-9][0-9]{0,17}$'
            THEN current_setting('app.current_user_id', true)::BIGINT
        ELSE NULL
    END;
$$;

REVOKE ALL ON FUNCTION app_current_user_id() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_current_user_id() TO app_backend;

-- ----------------------------------------------------------------------------
-- 4. MODELAGEM DAS TABELAS
-- ----------------------------------------------------------------------------

-- 4.1 Usuário
CREATE TABLE usuario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome_completo VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,

    -- CPF não é armazenado em claro.
    -- cpf_cifrado: ciphertext produzido pela aplicação.
    -- cpf_lookup_hmac: HMAC determinístico usado para unicidade/lookup.
    cpf_cifrado BYTEA,
    cpf_lookup_hmac BYTEA,

    hash_senha VARCHAR(255) NOT NULL,
    perfil perfil_usuario_enum NOT NULL DEFAULT 'ALUNO',
    status_conta status_conta_enum NOT NULL DEFAULT 'ATIVA',

    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    excluido_em TIMESTAMPTZ,

    CONSTRAINT ck_usuario_nome_nao_vazio
        CHECK (length(btrim(nome_completo)) >= 3),
    CONSTRAINT ck_usuario_email_minimo
        CHECK (position('@' in email) > 1),
    CONSTRAINT ck_usuario_hash_nao_vazio
        CHECK (length(btrim(hash_senha)) >= 20),
    CONSTRAINT ck_usuario_cpf_aluno
        CHECK (
            excluido_em IS NOT NULL
            OR perfil <> 'ALUNO'
            OR (cpf_cifrado IS NOT NULL AND cpf_lookup_hmac IS NOT NULL)
        ),
    CONSTRAINT ck_usuario_anonimizado
        CHECK (
            excluido_em IS NULL
            OR (
                status_conta = 'INATIVA'
                AND cpf_cifrado IS NULL
                AND cpf_lookup_hmac IS NULL
            )
        )
);

-- 4.2 Disciplina
CREATE TABLE disciplina (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    excluido_em TIMESTAMPTZ,

    CONSTRAINT ck_disciplina_codigo_nao_vazio
        CHECK (length(btrim(codigo)) > 0),
    CONSTRAINT ck_disciplina_nome_nao_vazio
        CHECK (length(btrim(nome)) >= 2)
);

-- 4.3 Turma
CREATE TABLE turma (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    disciplina_id BIGINT NOT NULL
        REFERENCES disciplina(id) ON DELETE RESTRICT,
    codigo VARCHAR(50) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    ano SMALLINT NOT NULL,
    semestre SMALLINT NOT NULL,
    status status_turma_enum NOT NULL DEFAULT 'ATIVA',
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    excluido_em TIMESTAMPTZ,

    CONSTRAINT ck_turma_codigo_nao_vazio
        CHECK (length(btrim(codigo)) > 0),
    CONSTRAINT ck_turma_nome_nao_vazio
        CHECK (length(btrim(nome)) >= 2),
    CONSTRAINT ck_turma_ano_valido
        CHECK (ano BETWEEN 2000 AND 2100),
    CONSTRAINT ck_turma_semestre_valido
        CHECK (semestre IN (1, 2)),
    CONSTRAINT ck_turma_excluida_nao_ativa
        CHECK (excluido_em IS NULL OR status <> 'ATIVA')
);

-- 4.4 Vínculo / matrícula
CREATE TABLE turma_usuario (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id BIGINT NOT NULL
        REFERENCES turma(id) ON DELETE RESTRICT,
    usuario_id BIGINT NOT NULL
        REFERENCES usuario(id) ON DELETE RESTRICT,
    papel papel_turma_enum NOT NULL,
    status status_matricula_enum NOT NULL DEFAULT 'ATIVA',
    inicio_em DATE NOT NULL DEFAULT CURRENT_DATE,
    fim_em DATE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    excluido_em TIMESTAMPTZ,

    CONSTRAINT ck_turma_usuario_datas
        CHECK (fim_em IS NULL OR fim_em >= inicio_em),
    CONSTRAINT ck_turma_usuario_excluido
        CHECK (excluido_em IS NULL OR status <> 'ATIVA')
);

-- 4.5 Conteúdo / mídia
CREATE TABLE conteudo (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turma_id BIGINT NOT NULL
        REFERENCES turma(id) ON DELETE RESTRICT,
    professor_id BIGINT NOT NULL
        REFERENCES usuario(id) ON DELETE RESTRICT,
    titulo VARCHAR(255) NOT NULL,
    descricao TEXT,
    tipo_material tipo_material_enum NOT NULL,

    -- Apenas identificador interno do objeto no Storage.
    -- NÃO armazenar URL pública ou URL assinada aqui.
    storage_path TEXT NOT NULL,

    mime_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64),

    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    excluido_em TIMESTAMPTZ,

    CONSTRAINT ck_conteudo_titulo_nao_vazio
        CHECK (length(btrim(titulo)) >= 2),
    CONSTRAINT ck_conteudo_storage_path_valido
        CHECK (
            length(btrim(storage_path)) BETWEEN 1 AND 2048
            AND lower(storage_path) NOT LIKE 'http://%'
            AND lower(storage_path) NOT LIKE 'https://%'
            AND storage_path NOT LIKE '//%'
        ),
    CONSTRAINT ck_conteudo_tamanho
        CHECK (tamanho_bytes >= 0),
    CONSTRAINT ck_conteudo_sha256
        CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
);

-- 4.6 Auditoria de acesso.
-- Particionada por data para suportar crescimento contínuo.
-- A PK inclui criado_em porque o PostgreSQL exige que a chave de
-- particionamento participe de PK/UNIQUE em tabela particionada.
CREATE TABLE log_acesso (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    usuario_id BIGINT NOT NULL DEFAULT app_current_user_id()
        REFERENCES usuario(id) ON DELETE RESTRICT,
    conteudo_id BIGINT NOT NULL
        REFERENCES conteudo(id) ON DELETE RESTRICT,
    acao acao_log_enum NOT NULL,
    ip_origem INET NOT NULL,
    user_agent TEXT,
    request_id UUID NOT NULL DEFAULT uuidv7(),
    detalhes JSONB,

    PRIMARY KEY (id, criado_em),

    CONSTRAINT ck_log_user_agent_tamanho
        CHECK (user_agent IS NULL OR length(user_agent) <= 4096)
) PARTITION BY RANGE (criado_em);

-- Cria 12 meses a partir do mês corrente + partição DEFAULT como rede de segurança.
-- A DEFAULT evita falha de escrita caso uma partição futura ainda não tenha sido
-- criada. A operação de manutenção deve criar novas partições regularmente.
DO $$
DECLARE
    v_mes DATE := date_trunc('month', CURRENT_DATE)::DATE;
    v_inicio DATE;
    v_fim DATE;
    v_nome TEXT;
BEGIN
    FOR i IN 0..11 LOOP
        v_inicio := (v_mes + make_interval(months => i))::DATE;
        v_fim := (v_mes + make_interval(months => i + 1))::DATE;
        v_nome := format('log_acesso_%s', to_char(v_inicio, 'YYYYMM'));

        EXECUTE format(
            'CREATE TABLE %I PARTITION OF public.log_acesso FOR VALUES FROM (%L) TO (%L)',
            v_nome,
            v_inicio,
            v_fim
        );
    END LOOP;
END;
$$;

CREATE TABLE log_acesso_default
    PARTITION OF log_acesso DEFAULT;

-- ----------------------------------------------------------------------------
-- 5. ÍNDICES
-- ----------------------------------------------------------------------------

CREATE UNIQUE INDEX idx_usuario_email_ativo
    ON usuario (lower(email))
    WHERE excluido_em IS NULL;

CREATE UNIQUE INDEX idx_usuario_cpf_lookup_hmac_ativo
    ON usuario (cpf_lookup_hmac)
    WHERE cpf_lookup_hmac IS NOT NULL AND excluido_em IS NULL;

CREATE UNIQUE INDEX idx_disciplina_codigo_ativo
    ON disciplina (lower(codigo))
    WHERE excluido_em IS NULL;

CREATE INDEX idx_turma_disciplina_periodo
    ON turma (disciplina_id, ano, semestre)
    WHERE excluido_em IS NULL;

CREATE UNIQUE INDEX idx_turma_codigo_ativo
    ON turma (lower(codigo))
    WHERE excluido_em IS NULL;

CREATE INDEX idx_turma_usuario_usuario_ativo
    ON turma_usuario (usuario_id, turma_id)
    WHERE status = 'ATIVA' AND excluido_em IS NULL;

CREATE INDEX idx_turma_usuario_turma_ativo
    ON turma_usuario (turma_id, usuario_id)
    WHERE status = 'ATIVA' AND excluido_em IS NULL;

CREATE UNIQUE INDEX idx_turma_usuario_unico_ativo
    ON turma_usuario (turma_id, usuario_id)
    WHERE status = 'ATIVA' AND excluido_em IS NULL;

CREATE INDEX idx_conteudo_turma_ativo
    ON conteudo (turma_id, criado_em DESC)
    WHERE excluido_em IS NULL;

CREATE INDEX idx_conteudo_professor_ativo
    ON conteudo (professor_id, criado_em DESC)
    WHERE excluido_em IS NULL;

CREATE UNIQUE INDEX idx_conteudo_storage_path_ativo
    ON conteudo (storage_path)
    WHERE excluido_em IS NULL;

CREATE INDEX idx_log_acesso_usuario_data
    ON log_acesso (usuario_id, criado_em DESC);

CREATE INDEX idx_log_acesso_conteudo_data
    ON log_acesso (conteudo_id, criado_em DESC);

CREATE INDEX idx_log_acesso_acao_data
    ON log_acesso (acao, criado_em DESC);

-- ----------------------------------------------------------------------------
-- 6. TRIGGERS DE INTEGRIDADE
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_trigger_set_timestamp()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    NEW.atualizado_em := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_usuario_atualizado_em
    BEFORE UPDATE ON usuario
    FOR EACH ROW EXECUTE FUNCTION fn_trigger_set_timestamp();

CREATE TRIGGER trg_disciplina_atualizado_em
    BEFORE UPDATE ON disciplina
    FOR EACH ROW EXECUTE FUNCTION fn_trigger_set_timestamp();

CREATE TRIGGER trg_turma_atualizado_em
    BEFORE UPDATE ON turma
    FOR EACH ROW EXECUTE FUNCTION fn_trigger_set_timestamp();

CREATE TRIGGER trg_turma_usuario_atualizado_em
    BEFORE UPDATE ON turma_usuario
    FOR EACH ROW EXECUTE FUNCTION fn_trigger_set_timestamp();

CREATE TRIGGER trg_conteudo_atualizado_em
    BEFORE UPDATE ON conteudo
    FOR EACH ROW EXECUTE FUNCTION fn_trigger_set_timestamp();

-- Garante que vínculos não apontem para usuário/turma inativos ou excluídos.
CREATE OR REPLACE FUNCTION fn_validar_turma_usuario()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM public.usuario u
         WHERE u.id = NEW.usuario_id
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
    ) THEN
        RAISE EXCEPTION 'Usuário inexistente, excluído, bloqueado ou inativo.'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM public.turma t
         WHERE t.id = NEW.turma_id
           AND t.status = 'ATIVA'
           AND t.excluido_em IS NULL
    ) THEN
        RAISE EXCEPTION 'Turma inexistente, excluída ou inativa.'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validar_turma_usuario
    BEFORE INSERT OR UPDATE ON turma_usuario
    FOR EACH ROW EXECUTE FUNCTION fn_validar_turma_usuario();

-- Garante que uma turma nova/alterada aponta para uma disciplina ativa.
CREATE OR REPLACE FUNCTION fn_validar_disciplina_da_turma()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM public.disciplina d
         WHERE d.id = NEW.disciplina_id
           AND d.excluido_em IS NULL
    ) THEN
        RAISE EXCEPTION 'A turma deve estar vinculada a uma disciplina ativa.'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validar_disciplina_da_turma
    BEFORE INSERT OR UPDATE OF disciplina_id ON turma
    FOR EACH ROW EXECUTE FUNCTION fn_validar_disciplina_da_turma();

-- Garante que o autor do conteúdo é realmente um DOCENTE ativo daquela turma.
CREATE OR REPLACE FUNCTION fn_validar_autor_conteudo()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM public.usuario u
          JOIN public.turma_usuario tu
            ON tu.usuario_id = u.id
         WHERE u.id = NEW.professor_id
           AND u.perfil IN ('DOCENTE', 'ADMIN')
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
           AND tu.turma_id = NEW.turma_id
           AND tu.papel = 'DOCENTE'
           AND tu.status = 'ATIVA'
           AND tu.excluido_em IS NULL
           AND tu.inicio_em <= CURRENT_DATE
           AND (tu.fim_em IS NULL OR tu.fim_em >= CURRENT_DATE)
    ) THEN
        RAISE EXCEPTION 'O autor do conteúdo deve possuir vínculo DOCENTE ativo na turma.'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validar_autor_conteudo
    BEFORE INSERT OR UPDATE ON conteudo
    FOR EACH ROW EXECUTE FUNCTION fn_validar_autor_conteudo();

-- ----------------------------------------------------------------------------
-- 7. FUNÇÕES DE AUTORIZAÇÃO
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.usuario u
         WHERE u.id = public.app_current_user_id()
           AND u.perfil = 'ADMIN'
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION usuario_tem_papel_na_turma(
    p_turma_id BIGINT,
    p_papel papel_turma_enum
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.turma_usuario tu
          JOIN public.turma t ON t.id = tu.turma_id
          JOIN public.usuario u ON u.id = tu.usuario_id
         WHERE tu.turma_id = p_turma_id
           AND tu.usuario_id = public.app_current_user_id()
           AND tu.papel = p_papel
           AND tu.status = 'ATIVA'
           AND tu.excluido_em IS NULL
           AND tu.inicio_em <= CURRENT_DATE
           AND (tu.fim_em IS NULL OR tu.fim_em >= CURRENT_DATE)
           AND t.status = 'ATIVA'
           AND t.excluido_em IS NULL
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION usuario_tem_papel_especifico_na_turma(
    p_usuario_id BIGINT,
    p_turma_id BIGINT,
    p_papel papel_turma_enum
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.turma_usuario tu
          JOIN public.turma t ON t.id = tu.turma_id
          JOIN public.usuario u ON u.id = tu.usuario_id
         WHERE tu.turma_id = p_turma_id
           AND tu.usuario_id = p_usuario_id
           AND tu.papel = p_papel
           AND tu.status = 'ATIVA'
           AND tu.excluido_em IS NULL
           AND tu.inicio_em <= CURRENT_DATE
           AND (tu.fim_em IS NULL OR tu.fim_em >= CURRENT_DATE)
           AND t.status = 'ATIVA'
           AND t.excluido_em IS NULL
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION usuario_pertence_a_turma(p_turma_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT public.usuario_tem_papel_na_turma(p_turma_id, 'DISCENTE')
        OR public.usuario_tem_papel_na_turma(p_turma_id, 'DOCENTE');
$$;

CREATE OR REPLACE FUNCTION usuario_pertence_a_disciplina(p_disciplina_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.turma t
          JOIN public.turma_usuario tu ON tu.turma_id = t.id
          JOIN public.usuario u ON u.id = tu.usuario_id
         WHERE t.disciplina_id = p_disciplina_id
           AND tu.usuario_id = public.app_current_user_id()
           AND tu.status = 'ATIVA'
           AND tu.excluido_em IS NULL
           AND tu.inicio_em <= CURRENT_DATE
           AND (tu.fim_em IS NULL OR tu.fim_em >= CURRENT_DATE)
           AND t.status = 'ATIVA'
           AND t.excluido_em IS NULL
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
    );
$$;

CREATE OR REPLACE FUNCTION usuario_pode_acessar_conteudo(p_conteudo_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.conteudo c
         WHERE c.id = p_conteudo_id
           AND c.excluido_em IS NULL
           AND (
               public.is_admin()
               OR public.usuario_pertence_a_turma(c.turma_id)
           )
    );
$$;

-- ----------------------------------------------------------------------------
-- 8. FUNÇÕES DE NEGÓCIO / DADOS SENSÍVEIS
-- ----------------------------------------------------------------------------

-- Criação de usuário somente por função controlada. O primeiro ADMIN deve ser
-- criado por bootstrap administrativo executado pelo processo de implantação.
CREATE OR REPLACE FUNCTION fn_criar_usuario(
    p_nome_completo VARCHAR,
    p_email VARCHAR,
    p_hash_senha VARCHAR,
    p_perfil perfil_usuario_enum DEFAULT 'ALUNO',
    p_cpf_cifrado BYTEA DEFAULT NULL,
    p_cpf_lookup_hmac BYTEA DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_id BIGINT;
BEGIN
    IF public.app_current_user_id() IS NULL OR NOT public.is_admin() THEN
        RAISE EXCEPTION 'Acesso negado: somente administradores podem criar usuários.'
            USING ERRCODE = '42501';
    END IF;

    IF p_perfil = 'ALUNO'
       AND (p_cpf_cifrado IS NULL OR p_cpf_lookup_hmac IS NULL) THEN
        RAISE EXCEPTION 'CPF é obrigatório para contas de ALUNO.'
            USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.usuario (
        nome_completo,
        email,
        hash_senha,
        perfil,
        status_conta,
        cpf_cifrado,
        cpf_lookup_hmac
    )
    VALUES (
        btrim(p_nome_completo),
        lower(btrim(p_email)),
        p_hash_senha,
        p_perfil,
        'ATIVA',
        p_cpf_cifrado,
        p_cpf_lookup_hmac
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION fn_atualizar_perfil_status_usuario(
    p_usuario_id BIGINT,
    p_perfil perfil_usuario_enum,
    p_status status_conta_enum
)
RETURNS VOID
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF public.app_current_user_id() IS NULL OR NOT public.is_admin() THEN
        RAISE EXCEPTION 'Acesso negado: operação restrita a administradores.'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.usuario
       SET perfil = p_perfil,
           status_conta = p_status
     WHERE id = p_usuario_id
       AND excluido_em IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Usuário não encontrado ou já anonimizado.'
            USING ERRCODE = 'P0002';
    END IF;
END;
$$;

-- Alteração de senha: o backend recebe apenas o hash já calculado.
-- Login: o backend recebe somente o hash da conta ativa encontrada pelo e-mail.
-- O hash não fica exposto por SELECT direto da tabela usuario.
CREATE OR REPLACE FUNCTION fn_obter_hash_senha_por_email(p_email VARCHAR)
RETURNS VARCHAR
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT u.hash_senha
      FROM public.usuario u
     WHERE lower(u.email) = lower(btrim(p_email))
       AND u.status_conta = 'ATIVA'
       AND u.excluido_em IS NULL
     LIMIT 1;
$$;

-- Watermark: somente o próprio usuário (ou ADMIN) pode obter os dados protegidos.
-- O CPF continua cifrado e a descriptografia ocorre fora do PostgreSQL.
CREATE OR REPLACE FUNCTION fn_obter_dados_watermark(p_usuario_id BIGINT)
RETURNS TABLE (
    nome_completo VARCHAR,
    cpf_cifrado BYTEA
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT u.nome_completo, u.cpf_cifrado
      FROM public.usuario u
     WHERE u.id = p_usuario_id
       AND u.excluido_em IS NULL
       AND (
           u.id = public.app_current_user_id()
           OR public.is_admin()
       );
$$;

CREATE OR REPLACE FUNCTION fn_atualizar_senha(
    p_usuario_id BIGINT,
    p_novo_hash VARCHAR
)
RETURNS VOID
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_executor_id BIGINT := public.app_current_user_id();
BEGIN
    IF v_executor_id IS NULL THEN
        RAISE EXCEPTION 'Sessão inválida ou não autenticada.'
            USING ERRCODE = '42000';
    END IF;

    IF v_executor_id <> p_usuario_id AND NOT public.is_admin() THEN
        RAISE EXCEPTION 'Acesso negado: sem permissão para alterar a senha deste usuário.'
            USING ERRCODE = '42501';
    END IF;

    IF length(btrim(p_novo_hash)) < 20 THEN
        RAISE EXCEPTION 'Hash de senha inválido.'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.usuario
       SET hash_senha = p_novo_hash,
           status_conta = CASE
               WHEN status_conta = 'BLOQUEADA' THEN 'ATIVA'
               ELSE status_conta
           END
     WHERE id = p_usuario_id
       AND excluido_em IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Usuário não encontrado ou inativo.'
            USING ERRCODE = 'P0002';
    END IF;
END;
$$;

-- Soft delete + anonimização. O histórico de logs permanece intacto.
CREATE OR REPLACE FUNCTION fn_anonimizar_usuario(p_usuario_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_executor_id BIGINT := public.app_current_user_id();
    v_hash_anonimo TEXT;
BEGIN
    IF v_executor_id IS NULL OR NOT public.is_admin() THEN
        RAISE EXCEPTION 'Acesso negado: operação restrita a administradores.'
            USING ERRCODE = '42501';
    END IF;

    IF v_executor_id = p_usuario_id THEN
        RAISE EXCEPTION 'O administrador não pode anonimizar a própria conta.'
            USING ERRCODE = '42501';
    END IF;

    v_hash_anonimo := md5(
        p_usuario_id::TEXT || clock_timestamp()::TEXT || pg_backend_pid()::TEXT
    );

    UPDATE public.usuario
       SET nome_completo = 'USUARIO_ANONIMIZADO_' || p_usuario_id,
           email = 'deleted_' || v_hash_anonimo || '@lgpd.internal',
           cpf_cifrado = NULL,
           cpf_lookup_hmac = NULL,
           hash_senha = 'DISABLED_ACCOUNT_' || v_hash_anonimo,
           status_conta = 'INATIVA',
           excluido_em = clock_timestamp()
     WHERE id = p_usuario_id
       AND excluido_em IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Usuário não encontrado ou já anonimizado.'
            USING ERRCODE = 'P0002';
    END IF;
END;
$$;

-- Criação de conteúdo controlada.
-- Professor comum somente pode criar conteúdo em seu próprio nome.
-- ADMIN pode criar em nome de um DOCENTE informado explicitamente.
CREATE OR REPLACE FUNCTION fn_criar_conteudo(
    p_turma_id BIGINT,
    p_professor_id BIGINT DEFAULT NULL,
    p_titulo VARCHAR DEFAULT NULL,
    p_descricao TEXT DEFAULT NULL,
    p_tipo_material tipo_material_enum DEFAULT NULL,
    p_storage_path TEXT DEFAULT NULL,
    p_mime_type VARCHAR DEFAULT NULL,
    p_tamanho_bytes BIGINT DEFAULT NULL,
    p_sha256 VARCHAR DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_executor_id BIGINT := public.app_current_user_id();
    v_professor_id BIGINT;
    v_id BIGINT;
BEGIN
    IF v_executor_id IS NULL THEN
        RAISE EXCEPTION 'Sessão inválida ou não autenticada.'
            USING ERRCODE = '42000';
    END IF;

    IF public.is_admin() THEN
        v_professor_id := p_professor_id;
        IF v_professor_id IS NULL THEN
            RAISE EXCEPTION 'ADMIN deve informar o professor autor.'
                USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NOT public.usuario_tem_papel_na_turma(p_turma_id, 'DOCENTE') THEN
            RAISE EXCEPTION 'Acesso negado: usuário não é docente da turma.'
                USING ERRCODE = '42501';
        END IF;
        v_professor_id := v_executor_id;
    END IF;

    IF NOT public.usuario_tem_papel_especifico_na_turma(
        v_professor_id, p_turma_id, 'DOCENTE'
    ) THEN
        RAISE EXCEPTION 'O professor autor deve possuir vínculo DOCENTE ativo na turma.'
            USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.conteudo (
        turma_id,
        professor_id,
        titulo,
        descricao,
        tipo_material,
        storage_path,
        mime_type,
        tamanho_bytes,
        sha256
    )
    VALUES (
        p_turma_id,
        v_professor_id,
        p_titulo,
        p_descricao,
        p_tipo_material,
        p_storage_path,
        p_mime_type,
        p_tamanho_bytes,
        lower(p_sha256)
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

-- Soft delete de conteúdo; não existe DELETE direto para o backend.
CREATE OR REPLACE FUNCTION fn_arquivar_conteudo(p_conteudo_id BIGINT)
RETURNS VOID
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_executor_id BIGINT := public.app_current_user_id();
BEGIN
    IF v_executor_id IS NULL THEN
        RAISE EXCEPTION 'Sessão inválida ou não autenticada.'
            USING ERRCODE = '42000';
    END IF;

    UPDATE public.conteudo c
       SET excluido_em = clock_timestamp()
     WHERE c.id = p_conteudo_id
       AND c.excluido_em IS NULL
       AND (
           public.is_admin()
           OR c.professor_id = v_executor_id
       );

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conteúdo não encontrado ou sem permissão para arquivamento.'
            USING ERRCODE = '42501';
    END IF;
END;
$$;

-- Auditoria append-only. user_id, timestamp e request_id são determinados pelo DB.
-- TENTATIVA_ACESSO_NEGADO pode ser registrada mesmo sem permissão ao conteúdo;
-- as demais ações exigem acesso válido à turma.
CREATE OR REPLACE FUNCTION fn_registrar_acesso(
    p_conteudo_id BIGINT,
    p_acao acao_log_enum,
    p_ip_origem INET,
    p_user_agent TEXT DEFAULT NULL,
    p_detalhes JSONB DEFAULT NULL
)
RETURNS UUID
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
DECLARE
    v_usuario_id BIGINT := public.app_current_user_id();
    v_tipo tipo_material_enum;
    v_turma_id BIGINT;
    v_sha256 VARCHAR(64);
    v_request_id UUID;
BEGIN
    IF v_usuario_id IS NULL THEN
        RAISE EXCEPTION 'Sessão inválida ou não autenticada.'
            USING ERRCODE = '42000';
    END IF;

    SELECT c.tipo_material, c.turma_id, c.sha256
      INTO v_tipo, v_turma_id, v_sha256
      FROM public.conteudo c
     WHERE c.id = p_conteudo_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Conteúdo inexistente.'
            USING ERRCODE = 'P0002';
    END IF;

    IF p_acao <> 'TENTATIVA_ACESSO_NEGADO'
       AND NOT public.usuario_pode_acessar_conteudo(p_conteudo_id) THEN
        RAISE EXCEPTION 'Usuário sem permissão para registrar esta ação.'
            USING ERRCODE = '42501';
    END IF;

    IF p_acao = 'ABRIU_PDF'
       AND v_tipo NOT IN ('PDF_LIVRO', 'PDF_APOSTILA') THEN
        RAISE EXCEPTION 'ABRIU_PDF exige conteúdo PDF.'
            USING ERRCODE = '23514';
    END IF;

    IF p_acao IN ('INICIOU_VIDEO', 'ASSISTIU_VIDEO')
       AND v_tipo <> 'VIDEO_AULA' THEN
        RAISE EXCEPTION 'Ação de vídeo incompatível com o tipo de conteúdo.'
            USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.log_acesso (
        usuario_id,
        conteudo_id,
        acao,
        ip_origem,
        user_agent,
        detalhes
    )
    VALUES (
        v_usuario_id,
        p_conteudo_id,
        p_acao,
        p_ip_origem,
        p_user_agent,
        COALESCE(p_detalhes, '{}'::JSONB) ||
            jsonb_build_object('conteudo_sha256_no_evento', v_sha256)
    )
    RETURNING request_id INTO v_request_id;

    RETURN v_request_id;
END;
$$;

-- ----------------------------------------------------------------------------
-- 9. RLS
-- ----------------------------------------------------------------------------

ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE disciplina ENABLE ROW LEVEL SECURITY;
ALTER TABLE turma ENABLE ROW LEVEL SECURITY;
ALTER TABLE turma_usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE conteudo ENABLE ROW LEVEL SECURITY;
ALTER TABLE log_acesso ENABLE ROW LEVEL SECURITY;

-- IMPORTANTE: não usamos FORCE RLS porque as funções SECURITY DEFINER são
-- propriedade do app_owner e precisam consultar as tabelas para avaliar as
-- regras. A role app_backend não é owner e não possui BYPASSRLS.

-- 9.1 Usuario
CREATE POLICY usuario_select_policy
    ON usuario
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            id = public.app_current_user_id()
            OR public.is_admin()
        )
    );

CREATE POLICY usuario_update_policy
    ON usuario
    FOR UPDATE
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            id = public.app_current_user_id()
            OR public.is_admin()
        )
    )
    WITH CHECK (
        excluido_em IS NULL
        AND (
            id = public.app_current_user_id()
            OR public.is_admin()
        )
    );

-- Não existe INSERT direto. Criação ocorre por fn_criar_usuario().

-- 9.2 Disciplina
CREATE POLICY disciplina_select_policy
    ON disciplina
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR public.usuario_pertence_a_disciplina(id)
        )
    );

CREATE POLICY disciplina_admin_insert_policy
    ON disciplina
    FOR INSERT
    TO app_backend
    WITH CHECK (public.is_admin());

CREATE POLICY disciplina_admin_update_policy
    ON disciplina
    FOR UPDATE
    TO app_backend
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- Não existe DELETE direto para preservar histórico.

-- 9.3 Turma
CREATE POLICY turma_select_policy
    ON turma
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR public.usuario_pertence_a_turma(id)
        )
    );

CREATE POLICY turma_admin_insert_policy
    ON turma
    FOR INSERT
    TO app_backend
    WITH CHECK (public.is_admin());

CREATE POLICY turma_admin_update_policy
    ON turma
    FOR UPDATE
    TO app_backend
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 9.4 Matrícula / vínculo
CREATE POLICY turma_usuario_select_policy
    ON turma_usuario
    FOR SELECT
    TO app_backend
    USING (
        public.is_admin()
        OR usuario_id = public.app_current_user_id()
        OR public.usuario_tem_papel_na_turma(turma_id, 'DOCENTE')
    );

CREATE POLICY turma_usuario_admin_insert_policy
    ON turma_usuario
    FOR INSERT
    TO app_backend
    WITH CHECK (public.is_admin());

CREATE POLICY turma_usuario_admin_update_policy
    ON turma_usuario
    FOR UPDATE
    TO app_backend
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 9.5 Conteúdo
CREATE POLICY conteudo_select_policy
    ON conteudo
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR public.usuario_pertence_a_turma(turma_id)
        )
    );

-- Professor só altera conteúdo que é seu. professor_id não recebe UPDATE direto.
CREATE POLICY conteudo_update_policy
    ON conteudo
    FOR UPDATE
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR professor_id = public.app_current_user_id()
        )
    )
    WITH CHECK (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR professor_id = public.app_current_user_id()
        )
    );

-- INSERT ocorre somente por fn_criar_conteudo().
-- DELETE físico não é concedido ao backend.

-- 9.6 Auditoria
CREATE POLICY log_acesso_select_policy
    ON log_acesso
    FOR SELECT
    TO app_backend
    USING (
        usuario_id = public.app_current_user_id()
        OR public.is_admin()
    );

-- INSERT/UPDATE/DELETE não são concedidos diretamente ao app_backend.
-- A única entrada é fn_registrar_acesso().

-- ----------------------------------------------------------------------------
-- 10. PRIVILÉGIOS DO RUNTIME
-- ----------------------------------------------------------------------------

-- Reafirma que app_backend não possui privilégios amplos herdados do ambiente.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM app_backend;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM app_backend;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM app_backend;
-- Não existe sintaxe ALL TYPES para este REVOKE. Os privilégios padrão de
-- TYPES para PUBLIC já foram revogados; o USAGE necessário é concedido abaixo.

GRANT USAGE ON SCHEMA public TO app_backend;

-- Tabelas: somente o necessário ao fluxo do backend.
GRANT SELECT (
    id,
    nome_completo,
    email,
    perfil,
    status_conta,
    criado_em,
    atualizado_em,
    excluido_em
) ON usuario TO app_backend;
GRANT UPDATE (nome_completo, email) ON usuario TO app_backend;

GRANT SELECT, INSERT, UPDATE ON disciplina TO app_backend;
GRANT SELECT, INSERT, UPDATE ON turma TO app_backend;
GRANT SELECT, INSERT, UPDATE ON turma_usuario TO app_backend;

GRANT SELECT, UPDATE (
    titulo,
    descricao,
    tipo_material,
    storage_path,
    mime_type,
    tamanho_bytes,
    sha256
) ON conteudo TO app_backend;

GRANT SELECT ON log_acesso TO app_backend;

-- USAGE é suficiente para uso normal de nextval(); não concedemos UPDATE/setval.
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO app_backend;

-- Tipos de domínio do contrato do backend.
GRANT USAGE ON TYPE perfil_usuario_enum, status_conta_enum, papel_turma_enum,
    status_turma_enum, status_matricula_enum, tipo_material_enum, acao_log_enum
    TO app_backend;

-- Funções de autorização / negócio.
REVOKE ALL ON FUNCTION is_admin() FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_tem_papel_na_turma(BIGINT, papel_turma_enum) FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_tem_papel_especifico_na_turma(BIGINT, BIGINT, papel_turma_enum) FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_pertence_a_turma(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_pertence_a_disciplina(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_pode_acessar_conteudo(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_criar_usuario(VARCHAR, VARCHAR, VARCHAR, perfil_usuario_enum, BYTEA, BYTEA) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_atualizar_perfil_status_usuario(BIGINT, perfil_usuario_enum, status_conta_enum) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_obter_hash_senha_por_email(VARCHAR) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_obter_dados_watermark(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_atualizar_senha(BIGINT, VARCHAR) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_anonimizar_usuario(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_criar_conteudo(BIGINT, BIGINT, VARCHAR, TEXT, tipo_material_enum, TEXT, VARCHAR, BIGINT, VARCHAR) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_arquivar_conteudo(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_registrar_acesso(BIGINT, acao_log_enum, INET, TEXT, JSONB) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_trigger_set_timestamp() FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_validar_turma_usuario() FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_validar_disciplina_da_turma() FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_validar_autor_conteudo() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION app_current_user_id() TO app_backend;
GRANT EXECUTE ON FUNCTION is_admin() TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_tem_papel_na_turma(BIGINT, papel_turma_enum) TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_tem_papel_especifico_na_turma(BIGINT, BIGINT, papel_turma_enum) TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_pertence_a_turma(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_pertence_a_disciplina(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_pode_acessar_conteudo(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_criar_usuario(VARCHAR, VARCHAR, VARCHAR, perfil_usuario_enum, BYTEA, BYTEA) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_atualizar_perfil_status_usuario(BIGINT, perfil_usuario_enum, status_conta_enum) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_obter_hash_senha_por_email(VARCHAR) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_obter_dados_watermark(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_atualizar_senha(BIGINT, VARCHAR) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_anonimizar_usuario(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_criar_conteudo(BIGINT, BIGINT, VARCHAR, TEXT, tipo_material_enum, TEXT, VARCHAR, BIGINT, VARCHAR) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_arquivar_conteudo(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_registrar_acesso(BIGINT, acao_log_enum, INET, TEXT, JSONB) TO app_backend;

-- ----------------------------------------------------------------------------
-- 11. DEFAULT PRIVILEGES PARA NOVOS OBJETOS CRIADOS POR app_owner
-- ----------------------------------------------------------------------------

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE ON SEQUENCES TO app_backend;

-- Nenhuma tabela nova deve ser exposta automaticamente.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC;

COMMIT;

-- ============================================================================
-- V2.1: correções aplicadas após validação real da V2:
--   1. Removida instrução inválida 'REVOKE ALL ON ALL TYPES ...'.
--   2. Restaurado EXECUTE de app_current_user_id() após o REVOKE global de funções.
--   3. Constraint de CPF permite ALUNO já anonimizado/excluído.
--   4. Bootstrap torna app_owner proprietário do schema public.
-- ============================================================================
-- NOTAS OPERACIONAIS
-- ============================================================================
-- 1. O backend deve abrir uma transação por request e executar, antes de
--    qualquer SELECT/UPDATE:
--      SET LOCAL app.current_user_id = '123';
--
-- 2. Nunca use SET persistente em connection pool. SET LOCAL expira no COMMIT
--    e evita vazamento de identidade entre requisições.
--
-- 3. O backend NÃO deve expor credenciais do PostgreSQL ao navegador.
--
-- 4. storage_path deve apontar para o objeto privado no Cloud Storage/Firebase.
--    URL assinada é efêmera e deve ser gerada pelo backend após a autorização.
--
-- 5. Watermark: backend consulta nome_completo + descriptografa cpf_cifrado
--    usando a infraestrutura de chaves da aplicação. O CPF em claro não deve
--    ser devolvido ao frontend fora do contexto necessário para a watermark.
--
-- 6. A partição DEFAULT é uma rede de segurança. Em operação normal, criar as
--    partições dos próximos meses antes de elas serem necessárias. Ao retirar
--    dados antigos, prefira DETACH/DROP de partições após política de retenção.
--
-- 7. Logs são dados potencialmente pessoais (IP/user-agent). Defina política
--    formal de retenção, acesso administrativo e descarte conforme LGPD.
--
-- 8. BACKUP/PITR, replicação, failover, pool de conexões, monitoramento e
--    storage externo são responsabilidades de infraestrutura e não são
--    resolvidos por este DDL isoladamente.
-- ============================================================================
