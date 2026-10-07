-- ============================================================================
-- TESTES RLS COM ASSERÇÕES - V2.1
-- Banco DESCARTÁVEL, depois de 00 (superusuário) e 01 (app_owner).
--
-- Uso:
--   psql -U postgres -P pager=off -v ON_ERROR_STOP=1 -d teste_v21 -f V2__rls_tests.sql
--
-- Estratégia:
--   * O seed é inserido como superusuário (bypassa RLS), para montar o cenário.
--   * Cada teste troca a identidade com set_config('app.current_user_id', ...)
--     e executa como app_backend (SET ROLE), que é o runtime real.
--   * Tudo roda em uma transação que termina com ROLLBACK: o banco fica limpo.
--
-- Atores:
--   ALUNO A  -> matriculado como DISCENTE na Turma 1
--   ALUNO B  -> matriculado como DISCENTE na Turma 2
--   DOCENTE C -> DOCENTE na Turma 1
--   DOCENTE E -> DOCENTE na Turma 2 (autor do conteúdo da Turma 2)
--   ADMIN D  -> administrador
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- SEED (como superusuário)
-- ----------------------------------------------------------------------------
INSERT INTO usuario (nome_completo, email, hash_senha, perfil, status_conta, cpf_cifrado, cpf_lookup_hmac)
VALUES
  ('Aluno A', 'a@rls.teste', 'hash_teste_com_mais_de_20_caracteres', 'ALUNO',   'ATIVA', '\x0a'::bytea, '\xa1'::bytea),
  ('Aluno B', 'b@rls.teste', 'hash_teste_com_mais_de_20_caracteres', 'ALUNO',   'ATIVA', '\x0b'::bytea, '\xb1'::bytea),
  ('Docente C', 'c@rls.teste', 'hash_teste_com_mais_de_20_caracteres', 'DOCENTE', 'ATIVA', NULL, NULL),
  ('Docente E', 'e@rls.teste', 'hash_teste_com_mais_de_20_caracteres', 'DOCENTE', 'ATIVA', NULL, NULL),
  ('Admin D', 'd@rls.teste', 'hash_teste_com_mais_de_20_caracteres', 'ADMIN',   'ATIVA', NULL, NULL);

INSERT INTO disciplina (codigo, nome) VALUES ('DISC-RLS', 'Disciplina de Teste');

INSERT INTO turma (disciplina_id, codigo, nome, ano, semestre)
SELECT d.id, 'T1-RLS', 'Turma 1', 2026, 1 FROM disciplina d WHERE d.codigo = 'DISC-RLS';
INSERT INTO turma (disciplina_id, codigo, nome, ano, semestre)
SELECT d.id, 'T2-RLS', 'Turma 2', 2026, 1 FROM disciplina d WHERE d.codigo = 'DISC-RLS';

INSERT INTO turma_usuario (turma_id, usuario_id, papel)
SELECT t.id, u.id, v.papel::papel_turma_enum
FROM (VALUES
    ('T1-RLS', 'a@rls.teste', 'DISCENTE'),
    ('T2-RLS', 'b@rls.teste', 'DISCENTE'),
    ('T1-RLS', 'c@rls.teste', 'DOCENTE'),
    ('T2-RLS', 'e@rls.teste', 'DOCENTE')
) AS v(turma_codigo, email, papel)
JOIN turma t ON t.codigo = v.turma_codigo
JOIN usuario u ON u.email = v.email;

-- Conteúdo da Turma 1 (autor C) e da Turma 2 (autor E). Insert direto, como
-- superusuário; o trigger fn_validar_autor_conteudo ainda é executado.
INSERT INTO conteudo (turma_id, professor_id, titulo, tipo_material, storage_path, mime_type, tamanho_bytes)
SELECT t.id, u.id, 'Apostila T1', 'PDF_APOSTILA', 'storage/t1/apostila.pdf', 'application/pdf', 1000
FROM turma t, usuario u WHERE t.codigo = 'T1-RLS' AND u.email = 'c@rls.teste';
INSERT INTO conteudo (turma_id, professor_id, titulo, tipo_material, storage_path, mime_type, tamanho_bytes)
SELECT t.id, u.id, 'Apostila T2', 'PDF_APOSTILA', 'storage/t2/apostila.pdf', 'application/pdf', 1000
FROM turma t, usuario u WHERE t.codigo = 'T2-RLS' AND u.email = 'e@rls.teste';

-- Guarda os IDs para os testes (variáveis de sessão da transação).
SELECT set_config('test.a',  (SELECT id::text FROM usuario WHERE email = 'a@rls.teste'), false);
SELECT set_config('test.b',  (SELECT id::text FROM usuario WHERE email = 'b@rls.teste'), false);
SELECT set_config('test.c',  (SELECT id::text FROM usuario WHERE email = 'c@rls.teste'), false);
SELECT set_config('test.e',  (SELECT id::text FROM usuario WHERE email = 'e@rls.teste'), false);
SELECT set_config('test.d',  (SELECT id::text FROM usuario WHERE email = 'd@rls.teste'), false);
SELECT set_config('test.t1', (SELECT id::text FROM turma WHERE codigo = 'T1-RLS'), false);
SELECT set_config('test.t2', (SELECT id::text FROM turma WHERE codigo = 'T2-RLS'), false);
SELECT set_config('test.c1', (SELECT id::text FROM conteudo WHERE titulo = 'Apostila T1'), false);
SELECT set_config('test.c2', (SELECT id::text FROM conteudo WHERE titulo = 'Apostila T2'), false);

-- ----------------------------------------------------------------------------
-- LEITURA POR TURMA (RLS de conteudo e turma)
-- ----------------------------------------------------------------------------

-- T1 (ALUNO A): A vê o conteúdo da Turma 1.
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM conteudo WHERE id = current_setting('test.c1')::bigint;
    RESET ROLE;
    IF n <> 1 THEN RAISE EXCEPTION 'T1 FALHOU: ALUNO A deveria ver conteúdo da Turma 1 (viu %)', n; END IF;
    RAISE NOTICE 'T1 OK: ALUNO A vê conteúdo da própria turma';
END $$;

-- T2 (ALUNO A): A NÃO vê o conteúdo da Turma 2.
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM conteudo WHERE id = current_setting('test.c2')::bigint;
    RESET ROLE;
    IF n <> 0 THEN RAISE EXCEPTION 'T2 FALHOU: ALUNO A viu conteúdo da Turma 2 (isolamento quebrado)'; END IF;
    RAISE NOTICE 'T2 OK: ALUNO A não vê conteúdo de outra turma';
END $$;

-- T3 (ALUNO B): B NÃO vê o conteúdo da Turma 1.
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.b'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM conteudo WHERE id = current_setting('test.c1')::bigint;
    RESET ROLE;
    IF n <> 0 THEN RAISE EXCEPTION 'T3 FALHOU: ALUNO B viu conteúdo da Turma 1'; END IF;
    RAISE NOTICE 'T3 OK: ALUNO B não vê conteúdo da Turma 1';
END $$;

-- T4 (DOCENTE C): C vê a Turma 1 e NÃO vê a Turma 2.
DO $$
DECLARE n1 INT; n2 INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.c'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n1 FROM conteudo WHERE id = current_setting('test.c1')::bigint;
    SELECT count(*) INTO n2 FROM conteudo WHERE id = current_setting('test.c2')::bigint;
    RESET ROLE;
    IF n1 <> 1 OR n2 <> 0 THEN
        RAISE EXCEPTION 'T4 FALHOU: DOCENTE C deveria ver só T1 (viu T1=%, T2=%)', n1, n2;
    END IF;
    RAISE NOTICE 'T4 OK: DOCENTE C vê só o conteúdo da própria turma';
END $$;

-- T5 (ADMIN D): D vê conteúdo das duas turmas.
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.d'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM conteudo WHERE id IN (current_setting('test.c1')::bigint, current_setting('test.c2')::bigint);
    RESET ROLE;
    IF n <> 2 THEN RAISE EXCEPTION 'T5 FALHOU: ADMIN deveria ver 2 conteúdos (viu %)', n; END IF;
    RAISE NOTICE 'T5 OK: ADMIN vê conteúdo de todas as turmas';
END $$;

-- T6 (ALUNO A): A NÃO vê a Turma 2 em turma.
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM turma WHERE id = current_setting('test.t2')::bigint;
    RESET ROLE;
    IF n <> 0 THEN RAISE EXCEPTION 'T6 FALHOU: ALUNO A viu a Turma 2'; END IF;
    RAISE NOTICE 'T6 OK: ALUNO A não vê outra turma';
END $$;

-- T7 (ALUNO A): A vê só o próprio registro em usuario (sem vazar colegas).
DO $$
DECLARE n INT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    SELECT count(*) INTO n FROM usuario WHERE id = current_setting('test.b')::bigint;
    RESET ROLE;
    IF n <> 0 THEN RAISE EXCEPTION 'T7 FALHOU: ALUNO A enxerga registro de outro usuário'; END IF;
    RAISE NOTICE 'T7 OK: ALUNO A não enxerga outro usuário';
END $$;

-- T8 (ALUNO A): hash_senha não é exposto ao backend.
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM hash_senha FROM usuario WHERE id = current_setting('test.a')::bigint;
        RESET ROLE;
        RAISE EXCEPTION 'T8 FALHOU: app_backend conseguiu ler hash_senha';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T8 OK: hash_senha negado ao app_backend';
    END;
END $$;

-- ----------------------------------------------------------------------------
-- ESCRITA E FUNÇÕES DE NEGÓCIO
-- ----------------------------------------------------------------------------

-- T9 (ALUNO A): INSERT direto em conteudo é negado.
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    BEGIN
        INSERT INTO conteudo (turma_id, professor_id, titulo, tipo_material, storage_path, mime_type, tamanho_bytes)
        VALUES (current_setting('test.t1')::bigint, current_setting('test.c')::bigint,
                'Invasao', 'PDF_LIVRO', 'storage/x.pdf', 'application/pdf', 1);
        RESET ROLE;
        RAISE EXCEPTION 'T9 FALHOU: ALUNO A inseriu conteúdo diretamente';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T9 OK: INSERT direto em conteudo negado';
    END;
END $$;

-- T10 (ALUNO A): fn_criar_conteudo na própria turma é negado (não é docente).
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM fn_criar_conteudo(current_setting('test.t1')::bigint, NULL, 'Tentativa', NULL,
                                  'PDF_LIVRO', 'storage/y.pdf', 'application/pdf', 1, NULL);
        RESET ROLE;
        RAISE EXCEPTION 'T10 FALHOU: ALUNO criou conteúdo via fn_criar_conteudo';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T10 OK: ALUNO não cria conteúdo';
    END;
END $$;

-- T11 (DOCENTE C): fn_criar_conteudo na Turma 1 é permitido.
DO $$
DECLARE v_id BIGINT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.c'), false);
    SET ROLE app_backend;
    v_id := fn_criar_conteudo(current_setting('test.t1')::bigint, NULL, 'Material novo T1', NULL,
                              'PDF_LIVRO', 'storage/t1/novo.pdf', 'application/pdf', 1, NULL);
    RESET ROLE;
    IF v_id IS NULL THEN RAISE EXCEPTION 'T11 FALHOU: DOCENTE C não conseguiu criar conteúdo na própria turma'; END IF;
    RAISE NOTICE 'T11 OK: DOCENTE C cria conteúdo na própria turma';
END $$;

-- T12 (DOCENTE C): fn_criar_conteudo na Turma 2 é negado.
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.c'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM fn_criar_conteudo(current_setting('test.t2')::bigint, NULL, 'Invasao T2', NULL,
                                  'PDF_LIVRO', 'storage/t2/invasao.pdf', 'application/pdf', 1, NULL);
        RESET ROLE;
        RAISE EXCEPTION 'T12 FALHOU: DOCENTE C criou conteúdo em turma alheia';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T12 OK: DOCENTE C não cria conteúdo em turma alheia';
    END;
END $$;

-- T13 (ALUNO B): fn_registrar_acesso em conteúdo da Turma 1 é negado.
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.b'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM fn_registrar_acesso(current_setting('test.c1')::bigint, 'ABRIU_PDF', '10.0.0.1'::inet, 'teste', NULL);
        RESET ROLE;
        RAISE EXCEPTION 'T13 FALHOU: ALUNO B registrou acesso a conteúdo da Turma 1';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T13 OK: ALUNO B não registra acesso fora da turma';
    END;
END $$;

-- T14 (ALUNO A): fn_registrar_acesso na própria turma é permitido.
DO $$
DECLARE v_id UUID;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    v_id := fn_registrar_acesso(current_setting('test.c1')::bigint, 'ABRIU_PDF', '10.0.0.1'::inet, 'teste', NULL);
    RESET ROLE;
    IF v_id IS NULL THEN RAISE EXCEPTION 'T14 FALHOU: ALUNO A não registrou acesso na própria turma'; END IF;
    RAISE NOTICE 'T14 OK: ALUNO A registra acesso na própria turma';
END $$;

-- T15 (ALUNO A): fn_criar_usuario é negado (só ADMIN cria usuário).
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.a'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM fn_criar_usuario('Admin Falso', 'falso@rls.teste', 'hash_teste_com_mais_de_20_caracteres',
                                 'ADMIN', NULL, NULL);
        RESET ROLE;
        RAISE EXCEPTION 'T15 FALHOU: ALUNO A criou usuário';
    EXCEPTION WHEN insufficient_privilege THEN
        RESET ROLE;
        RAISE NOTICE 'T15 OK: ALUNO não cria usuário (inclusive ADMIN)';
    END;
END $$;

-- T16 (DOCENTE C): fn_arquivar_conteudo de conteúdo da Turma 2 é negado.
DO $$
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.c'), false);
    SET ROLE app_backend;
    BEGIN
        PERFORM fn_arquivar_conteudo(current_setting('test.c2')::bigint);
        RESET ROLE;
        RAISE EXCEPTION 'T16 FALHOU: DOCENTE C arquivou conteúdo de outro docente';
    EXCEPTION WHEN OTHERS THEN
        RESET ROLE;
        RAISE NOTICE 'T16 OK: DOCENTE C não arquiva conteúdo alheio (%)', SQLERRM;
    END;
END $$;

-- T17 (ADMIN D): ADMIN cria usuário ALUNO via fn_criar_usuario (com CPF).
DO $$
DECLARE v_id BIGINT;
BEGIN
    PERFORM set_config('app.current_user_id', current_setting('test.d'), false);
    SET ROLE app_backend;
    v_id := fn_criar_usuario('Aluno Novo', 'novo@rls.teste', 'hash_teste_com_mais_de_20_caracteres',
                             'ALUNO', '\x0c'::bytea, '\xc1'::bytea);
    RESET ROLE;
    IF v_id IS NULL THEN RAISE EXCEPTION 'T17 FALHOU: ADMIN não criou aluno'; END IF;
    RAISE NOTICE 'T17 OK: ADMIN cria aluno com CPF';
END $$;

-- ----------------------------------------------------------------------------
-- FIM: ROLLBACK remove todo o seed e os dados de teste.
-- ----------------------------------------------------------------------------
DO $$ BEGIN RAISE NOTICE '=== TESTES RLS V2.1: TODAS AS ASSERÇÕES PASSARAM (rollback a seguir) ==='; END $$;

ROLLBACK;
