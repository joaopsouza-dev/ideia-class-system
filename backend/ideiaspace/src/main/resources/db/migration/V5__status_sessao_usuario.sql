SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

-- Usada pelo filtro JWT a cada requisição autenticada: a sessão só vale enquanto a conta
-- estiver ATIVA, e o perfil efetivo é o armazenado no banco (não o da claim do token).
CREATE OR REPLACE FUNCTION fn_obter_status_sessao(p_usuario_id BIGINT)
RETURNS TABLE (
    perfil perfil_usuario_enum,
    status_conta status_conta_enum
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT u.perfil, u.status_conta
      FROM public.usuario u
     WHERE u.id = p_usuario_id
       AND u.excluido_em IS NULL;
$$;

REVOKE ALL ON FUNCTION fn_obter_status_sessao(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_obter_status_sessao(BIGINT) TO app_backend;

RESET ROLE;
