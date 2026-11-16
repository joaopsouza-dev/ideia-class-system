RESET ROLE;
SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

-- ============================================================================
-- Etapa 3 — Gerenciamento de materiais: estado de publicação do conteúdo.
--
-- * Novo tipo status_publicacao_enum (RASCUNHO | PUBLICADO) e coluna conteudo.status_publicacao.
-- * Conteúdos já existentes foram cadastrados antes do conceito de rascunho e já eram
--   visíveis aos alunos: por isso o DEFAULT da COLUNA permanece 'PUBLICADO' (também mantém
--   válidos os inserts diretos feitos pelo owner nos scripts SQL de teste). O backend nunca
--   depende desse default: fn_criar_conteudo recebe o status e assume 'RASCUNHO' quando omitido.
-- * RLS: rascunho só é visível ao ADMIN e ao professor autor (com vínculo DOCENTE ativo).
--   Alunos e demais docentes da turma enxergam apenas conteúdo PUBLICADO.
-- * app_backend recebe UPDATE somente na nova coluna; INSERT/DELETE continuam negados.
-- * V1, V2 e V3 não foram alteradas.
-- ============================================================================

CREATE TYPE status_publicacao_enum AS ENUM ('RASCUNHO', 'PUBLICADO');

ALTER TABLE conteudo
    ADD COLUMN status_publicacao status_publicacao_enum NOT NULL DEFAULT 'PUBLICADO';

CREATE INDEX idx_conteudo_turma_status_ativo
    ON conteudo (turma_id, status_publicacao, criado_em DESC)
    WHERE excluido_em IS NULL;

GRANT USAGE ON TYPE status_publicacao_enum TO app_backend;
GRANT UPDATE (status_publicacao) ON conteudo TO app_backend;

-- ----------------------------------------------------------------------------
-- RLS de leitura: rascunho não é visível a alunos.
-- ----------------------------------------------------------------------------
DROP POLICY IF EXISTS conteudo_select_policy ON conteudo;
CREATE POLICY conteudo_select_policy
    ON conteudo
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND (
            public.is_admin()
            OR (
                professor_id = public.app_current_user_id()
                AND public.usuario_tem_papel_na_turma(turma_id, 'DOCENTE')
            )
            OR (
                status_publicacao = 'PUBLICADO'
                AND public.usuario_pertence_a_turma(turma_id)
            )
        )
    );

-- Mantém a função de acesso (usada por fn_registrar_acesso) coerente com a política acima.
-- CREATE OR REPLACE preserva REVOKE/GRANT já aplicados a ela.
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
               OR (
                   c.professor_id = public.app_current_user_id()
                   AND public.usuario_tem_papel_na_turma(c.turma_id, 'DOCENTE')
               )
               OR (
                   c.status_publicacao = 'PUBLICADO'
                   AND public.usuario_pertence_a_turma(c.turma_id)
               )
           )
    );
$$;

-- ----------------------------------------------------------------------------
-- fn_criar_conteudo passa a receber o status de publicação (último parâmetro, com default).
-- A assinatura antiga é removida para que chamadas de 9 argumentos não fiquem ambíguas;
-- elas continuam válidas e resolvem para a nova função (status = RASCUNHO).
-- ----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_criar_conteudo(
    BIGINT, BIGINT, VARCHAR, TEXT, tipo_material_enum, TEXT, VARCHAR, BIGINT, VARCHAR
);

CREATE OR REPLACE FUNCTION fn_criar_conteudo(
    p_turma_id BIGINT,
    p_professor_id BIGINT DEFAULT NULL,
    p_titulo VARCHAR DEFAULT NULL,
    p_descricao TEXT DEFAULT NULL,
    p_tipo_material tipo_material_enum DEFAULT NULL,
    p_storage_path TEXT DEFAULT NULL,
    p_mime_type VARCHAR DEFAULT NULL,
    p_tamanho_bytes BIGINT DEFAULT NULL,
    p_sha256 VARCHAR DEFAULT NULL,
    p_status_publicacao status_publicacao_enum DEFAULT 'RASCUNHO'
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
        sha256,
        status_publicacao
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
        lower(p_sha256),
        COALESCE(p_status_publicacao, 'RASCUNHO')
    )
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION fn_criar_conteudo(
    BIGINT, BIGINT, VARCHAR, TEXT, tipo_material_enum, TEXT, VARCHAR, BIGINT, VARCHAR, status_publicacao_enum
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_criar_conteudo(
    BIGINT, BIGINT, VARCHAR, TEXT, tipo_material_enum, TEXT, VARCHAR, BIGINT, VARCHAR, status_publicacao_enum
) TO app_backend;

RESET ROLE;
