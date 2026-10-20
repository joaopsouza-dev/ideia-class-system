-- ============================================================================
-- SMOKE TESTS COM ASSERÇÕES - V2.1
-- Execute em banco DESCARTÁVEL, depois de 00_roles_bootstrap_v2_1.sql
-- (como superusuário) e V1__firstversion.sql (como app_owner).
--
-- Uso:
--   psql -v ON_ERROR_STOP=1 -d <banco_teste> -f V3__smoke_tests_asserted.sql
--
-- Cada bloco DO lança EXCEPTION em caso de falha. Com ON_ERROR_STOP=1 o psql
-- interrompe no primeiro erro. Se terminar sem erro, todas as asserções passaram.
-- Este arquivo testa catálogo e regressões; não substitui os testes de RLS
-- com usuários seed nem os testes de integração do backend.
-- ============================================================================

-- R1 (achado #1): a migration precisa ter concluído; tabelas e tipos existem.
DO $$
DECLARE v_count INT;
BEGIN
    SELECT count(*) INTO v_count
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name IN ('usuario','disciplina','turma','turma_usuario','conteudo','log_acesso');
    IF v_count <> 6 THEN
        RAISE EXCEPTION 'R1 FALHOU: esperadas 6 tabelas, encontradas %', v_count;
    END IF;
    RAISE NOTICE 'R1 OK: migration concluída (6 tabelas)';
END $$;

-- R2 (achado #4): schema public pertence a app_owner e app_owner tem CREATE.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT nspowner FROM pg_namespace WHERE nspname = 'public')) <> 'app_owner' THEN
        RAISE EXCEPTION 'R2 FALHOU: schema public não pertence a app_owner';
    END IF;
    IF NOT has_schema_privilege('app_owner', 'public', 'CREATE') THEN
        RAISE EXCEPTION 'R2 FALHOU: app_owner sem CREATE no schema public';
    END IF;
    RAISE NOTICE 'R2 OK: ownership e CREATE do schema';
END $$;

-- R3 (achado #2): app_backend precisa de EXECUTE em app_current_user_id().
DO $$
BEGIN
    IF NOT has_function_privilege('app_backend', 'public.app_current_user_id()', 'EXECUTE') THEN
        RAISE EXCEPTION 'R3 FALHOU: app_backend sem EXECUTE em app_current_user_id()';
    END IF;
    RAISE NOTICE 'R3 OK: EXECUTE em app_current_user_id()';
END $$;

-- R4 (achado #3): constraint de CPF permite ALUNO excluído/anonimizado.
-- Verifica pela definição, pois testar INSERT exige conhecer todas as colunas NOT NULL.
DO $$
DECLARE v_def TEXT;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO v_def
    FROM pg_constraint
    WHERE conrelid = 'public.usuario'::regclass AND conname = 'ck_usuario_cpf_aluno';
    IF v_def IS NULL THEN
        RAISE EXCEPTION 'R4 FALHOU: constraint ck_usuario_cpf_aluno não existe';
    END IF;
    IF v_def NOT LIKE '%excluido_em IS NOT NULL%' THEN
        RAISE EXCEPTION 'R4 FALHOU: ck_usuario_cpf_aluno não considera excluido_em. Definição: %', v_def;
    END IF;
    RAISE NOTICE 'R4 OK: ck_usuario_cpf_aluno libera registro excluído';
END $$;

-- R5 (achado #3, ponta a ponta): ALUNO anonimizado deve passar pela constraint.
-- Usa UPDATE direto como dono da tabela (app_owner) para simular o estado
-- que fn_anonimizar_usuario produz. Roda em transação com ROLLBACK.
DO $$
DECLARE v_id BIGINT;
BEGIN
    SET LOCAL ROLE app_owner;
    -- Cria um ALUNO ativo de teste (ajuste colunas NOT NULL se o DDL exigir mais).
    INSERT INTO public.usuario (nome_completo, email, hash_senha, perfil, status_conta,
                                cpf_cifrado, cpf_lookup_hmac)
    VALUES ('Aluno Teste Smoke', 'aluno.smoke@teste.local',
            'hash_de_teste_com_mais_de_20_caracteres', 'ALUNO', 'ATIVA',
            '\x00'::bytea, '\x01'::bytea)
    RETURNING id INTO v_id;

    -- Simula a anonimização (mesmo conjunto de colunas de fn_anonimizar_usuario).
    UPDATE public.usuario
       SET cpf_cifrado = NULL, cpf_lookup_hmac = NULL,
           status_conta = 'INATIVA', excluido_em = clock_timestamp(),
           hash_senha = 'DISABLED_ACCOUNT_smoke_0000000000000000000000000000'
     WHERE id = v_id;

    RAISE NOTICE 'R5 OK: ALUNO anonimizado aceito pela constraint (id=%)', v_id;
    RAISE EXCEPTION 'ROLLBACK_INTENCIONAL' USING ERRCODE = 'P0001';
EXCEPTION
    WHEN SQLSTATE 'P0001' THEN
        IF SQLERRM <> 'ROLLBACK_INTENCIONAL' THEN RAISE; END IF;
    WHEN check_violation THEN
        RAISE EXCEPTION 'R5 FALHOU: constraint bloqueou ALUNO anonimizado: %', SQLERRM;
END $$;

-- R6 (achado #2 / hardening): funções de trigger NÃO executáveis pelo runtime.
DO $$
BEGIN
    IF has_function_privilege('app_backend', 'public.fn_validar_autor_conteudo()', 'EXECUTE') THEN
        RAISE EXCEPTION 'R6 FALHOU: app_backend tem EXECUTE direto em função de trigger';
    END IF;
    RAISE NOTICE 'R6 OK: função de trigger sem EXECUTE para app_backend';
END $$;

-- R7: app_backend sem SUPERUSER / BYPASSRLS.
DO $$
DECLARE v_super BOOLEAN; v_bypass BOOLEAN;
BEGIN
    SELECT rolsuper, rolbypassrls INTO v_super, v_bypass
    FROM pg_roles WHERE rolname = 'app_backend';
    IF v_super OR v_bypass THEN
        RAISE EXCEPTION 'R7 FALHOU: app_backend com SUPERUSER=% ou BYPASSRLS=%', v_super, v_bypass;
    END IF;
    RAISE NOTICE 'R7 OK: app_backend sem SUPERUSER/BYPASSRLS';
END $$;

-- R8: RLS habilitado nas 6 tabelas.
DO $$
DECLARE v_faltando INT;
BEGIN
    SELECT count(*) INTO v_faltando
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname IN ('usuario','disciplina','turma','turma_usuario','conteudo','log_acesso')
      AND NOT c.relrowsecurity;
    IF v_faltando > 0 THEN
        RAISE EXCEPTION 'R8 FALHOU: % tabela(s) sem RLS habilitado', v_faltando;
    END IF;
    RAISE NOTICE 'R8 OK: RLS habilitado em todas as tabelas';
END $$;

-- Fim: se chegou aqui sem EXCEPTION, todas as asserções passaram.
DO $$ BEGIN RAISE NOTICE '=== SMOKE TESTS V2.1: TODAS AS ASSERÇÕES PASSARAM ==='; END $$;

-- ----------------------------------------------------------------------------
-- PENDENTE (não automatizado aqui): cenários RLS A/B/C/D com usuários seed
-- (ALUNO A/Turma 1, ALUNO B/Turma 2, DOCENTE C/Turma 1, ADMIN D). Exigem
-- conhecer as assinaturas de fn_criar_usuario e fn_criar_conteudo.
-- ----------------------------------------------------------------------------
