SET LOCAL TIME ZONE 'UTC';
SET ROLE app_owner;

-- Ajusta a política de SELECT de disciplina para permitir que o administrador visualize
-- registros excluídos logicamente, viabilizando o soft-delete (UPDATE excluido_em = now())
-- sem violar a política de RLS, mantendo o filtro excluido_em IS NULL para não-administradores.
DROP POLICY IF EXISTS disciplina_select_policy ON disciplina;
CREATE POLICY disciplina_select_policy
    ON disciplina
    FOR SELECT
    TO app_backend
    USING (
        public.is_admin()
        OR (
            excluido_em IS NULL
            AND public.usuario_pertence_a_disciplina(id)
        )
    );

-- Ajusta a política de SELECT de turma para permitir que o administrador visualize
-- registros excluídos logicamente, viabilizando o soft-delete (UPDATE excluido_em = now())
-- sem violar a política de RLS, mantendo o filtro excluido_em IS NULL para não-administradores.
DROP POLICY IF EXISTS turma_select_policy ON turma;
CREATE POLICY turma_select_policy
    ON turma
    FOR SELECT
    TO app_backend
    USING (
        public.is_admin()
        OR (
            excluido_em IS NULL
            AND public.usuario_pertence_a_turma(id)
        )
    );

RESET ROLE;
