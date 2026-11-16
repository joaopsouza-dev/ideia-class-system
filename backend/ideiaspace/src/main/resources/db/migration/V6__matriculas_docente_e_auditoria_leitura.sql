-- V6 (aditiva): matrícula de alunos por docentes, consulta de usuários para matrícula
-- e garantia de unicidade de vínculos ativos. Não altera migrations anteriores.
-- Padrão de execução igual ao das migrations V3 e V5: SET ROLE app_owner / RESET ROLE.

SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

-- ---------------------------------------------------------------------------
-- 1) Funções auxiliares (SECURITY DEFINER, sem expor colunas sensíveis)
-- ---------------------------------------------------------------------------

-- Verdadeiro para administradores e para docentes ativos em pelo menos uma turma ativa.
CREATE OR REPLACE FUNCTION fn_usuario_atual_pode_gerir_matriculas()
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT public.is_admin()
        OR EXISTS (
            SELECT 1
              FROM public.turma_usuario tu
              JOIN public.turma t ON t.id = tu.turma_id
              JOIN public.usuario u ON u.id = tu.usuario_id
             WHERE tu.usuario_id = public.app_current_user_id()
               AND tu.papel = 'DOCENTE'
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

-- Verdadeiro quando o aluno informado está matriculado (ativo) em turma em que o usuário atual é docente.
CREATE OR REPLACE FUNCTION usuario_compartilha_turma_com_docente_atual(p_usuario_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.turma_usuario aluno_tu
          JOIN public.turma_usuario docente_tu ON docente_tu.turma_id = aluno_tu.turma_id
         WHERE aluno_tu.usuario_id = p_usuario_id
           AND aluno_tu.papel = 'DISCENTE'
           AND aluno_tu.status = 'ATIVA'
           AND aluno_tu.excluido_em IS NULL
           AND docente_tu.usuario_id = public.app_current_user_id()
           AND docente_tu.papel = 'DOCENTE'
           AND docente_tu.status = 'ATIVA'
           AND docente_tu.excluido_em IS NULL
    );
$$;

-- ---------------------------------------------------------------------------
-- 2) Consultas usadas pela matrícula (retornam apenas o necessário; sem CPF, sem hash)
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_obter_usuario_para_matricula(p_usuario_id BIGINT)
RETURNS TABLE (
    out_id BIGINT,
    out_nome_completo VARCHAR,
    out_email VARCHAR,
    out_perfil TEXT,
    out_status_conta TEXT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF NOT public.fn_usuario_atual_pode_gerir_matriculas() THEN
        RAISE EXCEPTION 'Acesso negado: apenas administradores e docentes podem consultar usuários para matrícula.'
            USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
        SELECT u.id,
               u.nome_completo,
               u.email,
               u.perfil::TEXT,
               u.status_conta::TEXT
          FROM public.usuario u
         WHERE u.id = p_usuario_id
           AND u.excluido_em IS NULL;
END;
$$;

CREATE OR REPLACE FUNCTION fn_listar_alunos_disponiveis()
RETURNS TABLE (
    out_id BIGINT,
    out_nome_completo VARCHAR,
    out_email VARCHAR
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
BEGIN
    IF NOT public.fn_usuario_atual_pode_gerir_matriculas() THEN
        RAISE EXCEPTION 'Acesso negado: apenas administradores e docentes podem listar alunos.'
            USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
        SELECT u.id, u.nome_completo, u.email
          FROM public.usuario u
         WHERE u.perfil = 'ALUNO'
           AND u.status_conta = 'ATIVA'
           AND u.excluido_em IS NULL
         ORDER BY u.nome_completo, u.id;
END;
$$;

-- ---------------------------------------------------------------------------
-- 3) Políticas RLS adicionais (são permissivas e somam-se às existentes com OR)
-- ---------------------------------------------------------------------------

-- Docente enxerga o cadastro dos alunos das próprias turmas (nomes na lista de participantes).
CREATE POLICY usuario_docente_select_alunos_policy
    ON usuario
    FOR SELECT
    TO app_backend
    USING (
        excluido_em IS NULL
        AND perfil = 'ALUNO'
        AND public.usuario_compartilha_turma_com_docente_atual(id)
    );

-- Docente matricula somente DISCENTE em turma onde ele é DOCENTE.
CREATE POLICY turma_usuario_docente_insert_discente_policy
    ON turma_usuario
    FOR INSERT
    TO app_backend
    WITH CHECK (
        papel = 'DISCENTE'
        AND public.usuario_tem_papel_na_turma(turma_id, 'DOCENTE')
    );

-- Docente remove (soft-delete) somente vínculos DISCENTE das próprias turmas.
CREATE POLICY turma_usuario_docente_update_discente_policy
    ON turma_usuario
    FOR UPDATE
    TO app_backend
    USING (
        papel = 'DISCENTE'
        AND public.usuario_tem_papel_na_turma(turma_id, 'DOCENTE')
    )
    WITH CHECK (
        papel = 'DISCENTE'
        AND public.usuario_tem_papel_na_turma(turma_id, 'DOCENTE')
    );

-- ---------------------------------------------------------------------------
-- 4) Permissões de execução das novas funções
-- ---------------------------------------------------------------------------

REVOKE ALL ON FUNCTION fn_usuario_atual_pode_gerir_matriculas() FROM PUBLIC;
REVOKE ALL ON FUNCTION usuario_compartilha_turma_com_docente_atual(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_obter_usuario_para_matricula(BIGINT) FROM PUBLIC;
REVOKE ALL ON FUNCTION fn_listar_alunos_disponiveis() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION fn_usuario_atual_pode_gerir_matriculas() TO app_backend;
GRANT EXECUTE ON FUNCTION usuario_compartilha_turma_com_docente_atual(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_obter_usuario_para_matricula(BIGINT) TO app_backend;
GRANT EXECUTE ON FUNCTION fn_listar_alunos_disponiveis() TO app_backend;

-- ---------------------------------------------------------------------------
-- 5) Unicidade de matrícula ativa por turma e aluno
--    Se já existirem duplicidades, a migration PARA com erro: nenhum dado é apagado automaticamente.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.turma_usuario
         WHERE status = 'ATIVA'
           AND excluido_em IS NULL
         GROUP BY turma_id, usuario_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Existem vínculos ativos duplicados em turma_usuario. Resolva-os manualmente antes de aplicar a V6.';
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_turma_usuario_vinculo_ativo
    ON turma_usuario (turma_id, usuario_id)
    WHERE status = 'ATIVA' AND excluido_em IS NULL;

RESET ROLE;
