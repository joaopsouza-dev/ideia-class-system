# README — Banco de Dados: Ideia Space v2.1
**Repositório Digital Protegido | Squad 22 | Porto Digital**

---

## Índice

1. [Subindo o banco com Docker Compose](#1-subindo-o-banco-com-docker-compose)
2. [Como funciona o RLS com app.current_user_id](#2-como-funciona-o-rls-com-appcurrent_user_id)
3. [Fluxo de integração: PostgreSQL ↔ Firebase Storage ↔ Frontend](#3-fluxo-de-integração-postgresql--firebase-storage--frontend)
4. [Checklist de validação da integração JPA](#4-checklist-de-validação-da-integração-jpa)

---

## 1. Subindo o banco com Docker Compose

### Pré-requisitos

- Docker Engine 24+ e Docker Compose v2+
- Arquivos SQL na pasta `./sql/`:
  - `01_schema.sql` — DDL completo (schema v2.1 com RLS e funções SECURITY DEFINER)
  - `02_seed_dev.sql` — Dados de teste (apenas para dev)

### Estrutura de pastas esperada

```
projeto/
├── Dockerfile
├── docker-compose.yml
└── sql/
    ├── 01_schema.sql
    └── 02_seed_dev.sql
```

### Comandos

```bash
# Primeira execução (cria volume e executa os scripts de init)
docker compose up -d

# Ver logs de inicialização (útil para diagnosticar erros de SQL)
docker compose logs -f ideiaspace_postgres

# Subir também o pgAdmin (interface visual)
docker compose --profile tools up -d

# Parar sem apagar dados
docker compose stop

# Parar E apagar os dados (reset completo)
docker compose down -v
```

### Acessando o banco diretamente

```bash
# Via psql dentro do container
docker exec -it ideiaspace_postgres psql -U ideiaspace_admin -d ideiaspace_db

# Verificar se o seed foi carregado
SELECT tabela, registros FROM (
    SELECT 'usuario' AS tabela, COUNT(*) AS registros FROM usuario
    UNION ALL SELECT 'conteudo', COUNT(*) FROM conteudo
    UNION ALL SELECT 'log_acesso', COUNT(*) FROM log_acesso
) t;
```

### Variáveis de ambiente (dev)

| Variável | Valor padrão (dev) | Descrição |
|---|---|---|
| `POSTGRES_DB` | `ideiaspace_db` | Nome do banco |
| `POSTGRES_USER` | `ideiaspace_admin` | Superusuário (só para migrações) |
| `POSTGRES_PASSWORD` | `dev_password_local_apenas` | ⚠️ Trocar em staging/prod |

> **Em produção:** use Docker Secrets ou variáveis de ambiente seguras — nunca credenciais em `docker-compose.yml`.

---

## 2. Como funciona o RLS com app.current_user_id

### Conceito

Row-Level Security (RLS) é uma funcionalidade do PostgreSQL que filtra automaticamente as linhas que cada conexão pode ver ou modificar, baseado em políticas definidas na tabela. No Ideia Space, a identidade do usuário é passada para o banco via uma **variável de configuração de sessão/transação**:

```sql
SET LOCAL app.current_user_id = '42';
```

### Por que SET LOCAL e não SET?

O Spring Boot usa um **pool de conexões** (HikariCP). Cada requisição HTTP reutiliza uma conexão do pool. Se usarmos `SET` (persistente na sessão), o `user_id` da requisição anterior "vaza" para a próxima requisição que pegar aquela conexão.

`SET LOCAL` limita a variável ao escopo da **transação atual** — ao fim do COMMIT ou ROLLBACK, a variável some automaticamente. Isso é seguro com pools de conexões.

```
Requisição HTTP → Spring Filter → SET LOCAL user_id='42' → JPA Queries (filtradas pelo RLS) → COMMIT → user_id apagado automaticamente
```

### Como o Spring Boot injeta o user_id

O `PostgresRLSInterceptor` (filtro Spring) faz isso automaticamente:

```java
// Executado antes de cada requisição autenticada
jdbcTemplate.execute("SET LOCAL app.current_user_id = '" + userId + "'");
```

O banco então lê essa variável nas políticas RLS:

```sql
-- Exemplo de política: aluno só vê conteúdo das suas turmas
CREATE POLICY policy_select_conteudo ON conteudo FOR SELECT
USING (
    is_current_user_admin()
    OR usuario_is_member_of_turma(current_app_user_id(), turma_id)
);
```

### Configuração crítica no application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ideiaspace_db
    username: app_backend          # Role com NOBYPASSRLS (sujeita ao RLS)
    password: ${DB_PASSWORD}
    hikari:
      auto-commit: false           # OBRIGATÓRIO: SET LOCAL só funciona dentro de transação
      maximum-pool-size: 10
      connection-timeout: 30000
```

> **Atenção:** `auto-commit: false` é obrigatório. Com auto-commit ativado, cada statement é uma transação separada e o `SET LOCAL` é descartado antes das queries JPA.

### Testando o isolamento RLS manualmente

```sql
-- Simula a role do backend (sujeita ao RLS)
SET ROLE app_role;

-- Simula usuário 4 (aluno Maria — membro da Turma 1)
SET LOCAL app.current_user_id = '4';
SELECT id, titulo FROM conteudo;  -- deve retornar apenas conteúdo da Turma 1

-- Troca para usuário 5 (aluno João — NÃO está na Turma 2)
SET LOCAL app.current_user_id = '5';
SELECT id, titulo FROM conteudo WHERE turma_id = 2;  -- deve retornar 0 linhas

RESET ROLE;
```

---

## 3. Fluxo de integração: PostgreSQL ↔ Firebase Storage ↔ Frontend

### Premissas de segurança

- O PostgreSQL armazena **apenas o caminho relativo** do arquivo no bucket (`caminho_storage`), nunca a URL pública.
- O Firebase Storage bucket é configurado como **privado** (sem acesso público).
- O backend gera **Signed URLs temporárias** (10 min por padrão) sob demanda — uma por requisição de acesso.
- O frontend consome o arquivo via canvas (PDF) ou streaming (vídeo), com scripts que bloqueiam print/download.

### Fluxo passo a passo

```
1. Aluno faz login → Spring Security valida credenciais → JWT emitido

2. Aluno acessa GET /api/conteudo/{uuid_publico}
   ├── PostgresRLSInterceptor: SET LOCAL app.current_user_id = '<id>'
   ├── JPA: SELECT * FROM conteudo WHERE uuid_publico = ? AND removido_em IS NULL
   │    └── RLS verifica: usuario_is_member_of_turma(aluno_id, turma_id) → TRUE
   └── Retorna: { titulo, tipo_material, ... } (sem caminho_storage)

3. Aluno acessa GET /api/conteudo/{uuid_publico}/signed-url
   ├── Backend busca caminho_storage do conteúdo no banco
   ├── FirebaseStorageService.gerarSignedUrl(caminho_storage, 10, MINUTES)
   │    └── Google Cloud Storage API gera URL V4 assinada com service account
   └── Retorna: { signedUrl: "https://storage.googleapis.com/bucket/...?X-Goog-Signature=..." }

4. Frontend usa a signedUrl para:
   ├── PDF: renderiza via PDF.js em <canvas> (não como <embed> — bloqueia copy/print)
   └── Vídeo: stream via <video> com src=signedUrl (sem download attr)

5. Evento de acesso é registrado:
   INSERT INTO log_acesso (usuario_id, conteudo_id, tipo_acao, ip_origem, dispositivo, data_hora)
   VALUES (...);
   -- Trigger prevent_log_tampering torna o registro imutável

6. Signed URL expira após 10 minutos → acesso ao Storage negado automaticamente
```

### Configuração do Firebase Admin SDK

```yaml
# application.yml
firebase:
  storage:
    bucket-name: ideiaspace-seu-projeto.appspot.com
    credentials-path: classpath:firebase/serviceAccountKey.json
    signed-url-duration-minutes: 10
```

> O arquivo `serviceAccountKey.json` deve ser gerado em:
> Firebase Console → Configurações do Projeto → Contas de Serviço → Gerar nova chave privada

**⚠️ Nunca faça commit do `serviceAccountKey.json` no repositório Git.**  
Adicione ao `.gitignore`: `src/main/resources/firebase/`

---

## 4. Checklist de validação da integração JPA

### 4.1 Banco de dados

- [ ] `docker compose up -d` sobe sem erros
- [ ] `docker compose logs ideiaspace_postgres` não mostra erros de SQL
- [ ] Verificação de seed: todas as tabelas com registros esperados
- [ ] `SELECT current_user;` dentro do container retorna `ideiaspace_admin`

### 4.2 Spring Boot ↔ PostgreSQL

- [ ] `application.yml` tem `auto-commit: false` no HikariCP
- [ ] `datasource.username` é `app_role` (ou `app_backend`) — **não** o superuser
- [ ] `PostgresRLSInterceptor` está anotado com `@Component` e visível no contexto Spring
- [ ] Requisição autenticada: verificar no log `[RLS] app.current_user_id definido para userId=X`
- [ ] Requisição não autenticada (ex.: `/api/auth/login`): sem erro de RLS

### 4.3 Isolamento RLS por perfil

| Cenário | Esperado |
|---|---|
| Aluno acessa `GET /conteudo` | Só retorna conteúdos das turmas em que está matriculado |
| Aluno tenta acessar conteúdo de outra turma | 404 (RLS retorna 0 linhas) |
| Professor acessa `GET /conteudo` | Retorna conteúdos de todas as turmas que leciona |
| ADMIN acessa qualquer recurso | Acesso total (is_current_user_admin() retorna TRUE) |
| Aluno inativo tenta logar | 401 (isEnabled() = false no UserDetails) |

### 4.4 Imutabilidade do log_acesso

```sql
-- Este UPDATE deve falhar com exceção:
UPDATE log_acesso SET tipo_acao = 'ABRIU_PDF' WHERE id = 1;
-- Esperado: ERROR: Operação negada: registros de auditoria em log_acesso são imutáveis.

-- Este DELETE também deve falhar:
DELETE FROM log_acesso WHERE id = 1;
-- Esperado: mesma mensagem de erro
```

### 4.5 Firebase Storage

- [ ] `FirebaseStorageService` inicializa sem erro (verificar log `[Firebase] Storage inicializado`)
- [ ] `GET /api/conteudo/{uuid}/signed-url` retorna URL válida (começa com `https://storage.googleapis.com/`)
- [ ] URL expira após o tempo configurado (testar acesso após 10+ minutos)
- [ ] URL de um conteúdo de turma diferente **não deve ser gerada** (RLS impediu o SELECT do caminho_storage)

### 4.6 Soft delete

```bash
# Conteúdo removido não aparece nas listagens
GET /api/conteudo → não retorna itens com removido_em IS NOT NULL

# Mas o log ainda existe (integridade referencial preservada)
SELECT COUNT(*) FROM log_acesso WHERE conteudo_id = <id_removido>;
-- Deve retornar > 0 (os acessos históricos estão preservados)
```

---

**Dúvidas ou problemas?** Abra uma issue no repositório do Squad 22 ou contate:  
Victor Emanoel · André Luiz · Grupo 3 — Banco de Dados e Infraestrutura
