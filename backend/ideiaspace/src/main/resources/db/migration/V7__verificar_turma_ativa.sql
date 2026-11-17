-- Verificação booleana da existência de uma turma ativa, independente da RLS
-- da sessão chamadora. A função pertence a app_owner (proprietário das tabelas),
-- e não revela dados da turma.
SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

CREATE OR REPLACE FUNCTION fn_turma_ativa_existe(p_turma_id BIGINT)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.turma t
         WHERE t.id = p_turma_id
           AND t.status = 'ATIVA'
           AND t.excluido_em IS NULL
    );
$$;

REVOKE ALL ON FUNCTION fn_turma_ativa_existe(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_turma_ativa_existe(BIGINT) TO app_backend;

RESET ROLE;
