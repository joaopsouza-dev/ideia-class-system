SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

CREATE OR REPLACE FUNCTION fn_obter_dados_login_por_email(p_email VARCHAR)
RETURNS TABLE (
    id BIGINT,
    nome_completo VARCHAR,
    email VARCHAR,
    perfil perfil_usuario_enum,
    status_conta status_conta_enum,
    hash_senha VARCHAR
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $$
    SELECT u.id,
           u.nome_completo,
           u.email,
           u.perfil,
           u.status_conta,
           u.hash_senha
      FROM public.usuario u
     WHERE lower(u.email) = lower(btrim(p_email))
       AND u.excluido_em IS NULL
     LIMIT 1;
$$;

REVOKE ALL ON FUNCTION fn_obter_dados_login_por_email(VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_obter_dados_login_por_email(VARCHAR) TO app_backend;

CREATE OR REPLACE FUNCTION fn_provisionar_usuario_inicial(
    p_nome_completo VARCHAR,
    p_email VARCHAR,
    p_hash_senha VARCHAR,
    p_perfil perfil_usuario_enum DEFAULT 'ADMIN',
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
    v_total INT;
BEGIN
    SELECT count(*) INTO v_total FROM public.usuario WHERE perfil = 'ADMIN' AND excluido_em IS NULL;
    IF v_total > 0 AND (public.app_current_user_id() IS NULL OR NOT public.is_admin()) THEN
        RAISE EXCEPTION 'Acesso negado: o sistema já possui administrador provisionado.'
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

REVOKE ALL ON FUNCTION fn_provisionar_usuario_inicial(VARCHAR, VARCHAR, VARCHAR, perfil_usuario_enum, BYTEA, BYTEA) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_provisionar_usuario_inicial(VARCHAR, VARCHAR, VARCHAR, perfil_usuario_enum, BYTEA, BYTEA) TO app_backend;

RESET ROLE;
