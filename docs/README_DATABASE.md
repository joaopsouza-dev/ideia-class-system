# Banco de dados e segurança — Ideia Space

Esta página descreve a configuração atual do repositório. Para executar o projeto, comece pelo [README principal](../README.md); este documento cobre PostgreSQL, migrations, RLS e o banco de testes.

## PostgreSQL no Docker Compose

O Compose está em `backend/compose.yaml`. O serviço `db` usa PostgreSQL 18.6 e publica a porta do container `5432` como `localhost:5434`. O banco de desenvolvimento chama-se `ideiaspace`.

```powershell
Set-Location .\backend
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
docker compose up -d db
docker compose ps
docker compose exec db pg_isready -U postgres -d ideiaspace
```

O modelo `.env.example` contém os nomes das variáveis exigidas; defina os valores localmente, sem os incluir em documentação, terminal compartilhado ou Git. O volume `postgres_data` preserva os dados entre reinícios. `docker compose down` para/remove os containers sem apagar o volume. Não use opções que removem volumes para manutenção comum.

Para abrir um cliente PostgreSQL no container:

```powershell
docker compose exec db psql -U postgres -d ideiaspace
```

Comandos úteis no `psql`:

```sql
\dt
\d+ public.turma
\df+ public.fn_criar_conteudo
\df+ public.fn_turma_ativa_existe
```

Uma sessão administrativa pode ver registros que RLS oculta de `app_backend`; ela serve para inspeção e manutenção, não representa o comportamento da aplicação.

## Migrations Flyway

As migrations ficam em `backend/ideiaspace/src/main/resources/db/migration/` e são aplicadas pelo Flyway ao iniciar o backend, com usuário de migração próprio. O conjunto versionado vai de V1 a V7. A V7 adiciona `fn_turma_ativa_existe`, que retorna apenas um booleano e permite distinguir uma turma ativa inexistente de uma turma invisível pela RLS. Não altere migrations já aplicadas; mudanças de banco devem ser aditivas.

A estrutura relacional principal é:

| Entidade/tabela | Responsabilidade |
|---|---|
| `usuario` | Contas, perfil geral e estado da conta |
| `disciplina` | Dados das disciplinas |
| `turma` | Turmas de uma disciplina |
| `turma_usuario` | Vínculo de usuário à turma e papel nela (`DOCENTE`/`DISCENTE`) |
| `conteudo` | Metadados, status de publicação e referência privada ao arquivo |
| `log_acesso` | Eventos de acesso e auditoria |

O PostgreSQL guarda metadados e caminho interno do objeto; os bytes ficam no provedor de armazenamento configurado. Consulte [Firebase Storage](../backend/docs/FIREBASE_SETUP.md) para a opção em nuvem.

## Row-Level Security (RLS)

As políticas RLS limitam linhas de acordo com a identidade atual e as regras de negócio. A role runtime `app_backend` não deve ser superusuária e não deve possuir `BYPASSRLS`. As migrations são executadas com `app_owner`; funções específicas com `SECURITY DEFINER` têm `search_path` fixo, recebem permissões explícitas e retornam somente os dados necessários.

O backend guarda o ID autenticado em contexto da requisição. O `RlsDataSource` aplica `set_config('app.current_user_id', ..., true)` na conexão dentro da transação, e o valor local desaparece ao terminar a transação. Isso evita deixar a identidade de uma requisição na conexão reutilizada pelo pool.

A criação de conteúdo usa `fn_criar_conteudo`, que repete no banco a validação de autorização, inclusive o vínculo docente. A verificação booleana da V7 não substitui essa validação nem altera as políticas RLS.

## Banco de testes

Os testes de integração devem usar uma base isolada chamada `ideiaspace_test`, separada do banco de desenvolvimento. Crie-a no servidor PostgreSQL de teste, sem substituir nem limpar o banco `ideiaspace`. Flyway aplica as migrations ao banco de testes quando a suíte sobe.

As propriedades em `backend/ideiaspace/src/test/resources/application.properties` definem:

- URL padrão `jdbc:postgresql://localhost:5432/ideiaspace_test`;
- usuário da aplicação `app_backend`;
- usuário Flyway `postgres` por padrão;
- senhas fornecidas pelo ambiente em `SPRING_TEST_DATASOURCE_PASSWORD` e `SPRING_TEST_FLYWAY_PASSWORD`.

Se usar o PostgreSQL do Compose, a porta do host é `5434`; configure `SPRING_TEST_DATASOURCE_URL` como `jdbc:postgresql://localhost:5434/ideiaspace_test`. Defina as senhas no ambiente do processo PowerShell/IDE que inicia o Maven. O arquivo `.env` do Compose não é carregado automaticamente por `mvnw.cmd`.

Execute a partir de `backend/ideiaspace/`:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
```

Os testes de integração criam e alteram dados de teste. Não aponte a configuração para o banco de desenvolvimento. Consulte o [README principal](../README.md#testes-do-backend) para os comandos completos.
