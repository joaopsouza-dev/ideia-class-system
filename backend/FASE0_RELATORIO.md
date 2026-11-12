# RELATÓRIO DE ALTERAÇÕES — FASE 0

## 1. Resumo executivo

A Fase 0 (contenção e segurança) foi executada sobre o backend original. Situação inicial:

- O endpoint `POST /api/v1/auth/provision` era público (`/api/v1/auth/**` com `permitAll`). Enquanto o banco não tinha administrador, qualquer pessoa conseguia criar uma conta `ADMIN` com uma requisição anônima. Depois que existia um administrador, a função `fn_provisionar_usuario_inicial` bloqueava chamadas sem usuário autenticado, então a falha ficava restrita à fase de "banco sem admin".
- Credenciais estavam versionadas: senhas de fallback em `application.properties` e `application-test`, senhas do Docker Compose, e um JWT secret e chaves CPF reais em `.idea/workspace.xml`.
- A role `app_backend` não recebia senha: o `00_roles_bootstrap_v2_1.sql` cria a role sem senha, enquanto o Compose informava `app_backend_password`. Na prática, o backend não autenticava no banco do Docker em um volume novo.
- O `storagePath` é recebido do cliente em `cadastrar` e `atualizar` (identificado e documentado, sem correção nesta fase).

Ao final, o provisionamento exige ADMIN autenticado, o primeiro administrador vem de um bootstrap controlado por variáveis de ambiente, as credenciais saíram dos arquivos versionados, e a role `app_backend` recebe a senha pelo `.env`.

**Limitação importante:** neste ambiente não foi possível baixar dependências Maven nem executar PostgreSQL. Por isso **os testes não foram executados**. Foi feita apenas verificação de sintaxe (ver seção 7).

## 2. Vulnerabilidades corrigidas

### 2.1 Provisionamento público de ADMIN (escalação de privilégios)
- **Problema:** `/api/v1/auth/provision` aceitava chamadas anônimas. Sem administrador no banco, qualquer pessoa criava um ADMIN.
- **Risco:** controle total do sistema (criação de administradores, acesso a todos os conteúdos e turmas).
- **Solução:** `SecurityConfig` passou a exigir `hasRole("ADMIN")` para `/api/v1/auth/provision`, antes da regra geral `permitAll`. `AuthController.provision` recebeu `@PreAuthorize("hasRole('ADMIN')")` como segunda barreira.
- **Como funciona agora:** sem token, a resposta é 401. Com token de ALUNO ou DOCENTE, a resposta é 403. Com token de ADMIN, o provisionamento segue normalmente. O perfil vem do token assinado (`ROLE_<perfil>`), e a função do banco continua conferindo `is_admin()` contra a tabela `usuario`.

### 2.2 Primeiro administrador sem mecanismo controlado
- **Problema:** com a rota fechada, nenhum administrador poderia ser criado.
- **Solução:** `config/FirstAdminBootstrap` (`ApplicationRunner`). Ele cria um ADMIN somente se `APP_BOOTSTRAP_ADMIN_NAME`, `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD` estiverem definidas, a senha tiver pelo menos 12 caracteres, e o banco não tiver administrador (verificado pela própria `fn_provisionar_usuario_inicial`). Não há rota pública e o perfil é fixo em ADMIN. Se as variáveis não existirem, o bootstrap não faz nada.
- **Como funciona agora:** no primeiro start com as variáveis preenchidas, o ADMIN é criado e um aviso é registrado no log. Nos starts seguintes, o banco já tem admin e o bootstrap é ignorado sem erro. Recomenda-se remover as variáveis depois do primeiro uso.

### 2.3 Segredos de exemplo aceitos silenciosamente
- **Problema:** `AppSecurityProperties` validava só presença e tamanho. Um valor como `troque_por_...` de 32+ caracteres subiria sem aviso.
- **Solução:** `validate()` agora rejeita, na inicialização, valores vazios, com menos de 32 caracteres ou contendo marcadores de exemplo (`change-me`, `changeme`, `troque`, `exemplo`, `example`, `placeholder`). Aplica-se a `APP_JWT_SECRET`, `APP_CPF_KEY` e `APP_CPF_HMAC_KEY`.
- **Como funciona agora:** a aplicação não sobe com segredo de exemplo, e a mensagem indica como gerar um segredo real.

### 2.4 Credenciais fixas nos arquivos versionados
- **Problema:** fallbacks de senha com valores reais em `application.properties` (`databasejoaopedro`, usuário `joaopedro`) e em `src/test/resources/application.properties`. Além disso, `DatabaseRlsSmokeTests` tinha os mesmos valores como default. `compose.yaml` tinha `postgres_dev_password` e `app_backend_password` em texto puro.
- **Risco:** acesso ao banco por quem tiver o repositório, e reutilização dessas senhas em outros ambientes.
- **Solução:** nenhum fallback de senha permanece. As variáveis são lidas do ambiente. O Compose usa `${VAR:?mensagem}`, então falha com mensagem clara se a variável faltar.
- **Como funciona agora:** para subir, é preciso criar o `.env` a partir de `.env.example`.

### 2.5 Role `app_backend` sem senha (incompatibilidade Compose × bootstrap)
- **Problema:** `00_roles_bootstrap_v2_1.sql` cria a role sem senha (e, por política do próprio arquivo, não deve conter senha). O Compose passava `app_backend_password`, mas nada a aplicava.
- **Risco:** o backend não conectava no banco do Docker, ou o administrador definia uma senha manualmente fora do versionamento.
- **Solução:** novo script `db/init/01_app_backend_password.sh`, executado pelo entrypoint do PostgreSQL após o `00_`. Ele lê `APP_BACKEND_DB_PASSWORD` do ambiente e executa `ALTER ROLE app_backend WITH PASSWORD :'app_backend_pw'`. A senha não é gravada no arquivo e o script falha se a variável estiver vazia. Privilégios, `NOBYPASSRLS`, `NOSUPERUSER` e as políticas de RLS não foram alterados.

### 2.6 Credenciais reais na configuração da IDE
- **Problema:** `.idea/workspace.xml` tinha, na configuração de execução "run 2", o JWT secret, as chaves CPF e as senhas do banco em texto puro.
- **Solução:** o bloco `<envs>` foi removido. O arquivo `.gitignore` da raiz do backend passou a ignorar `.idea/workspace.xml`.
- **Atenção:** esses valores **já foram expostos** neste zip (e em qualquer histórico Git onde existam). Veja a seção 8 (rotação obrigatória).

## 3. Arquivos modificados

| Caminho | O que foi alterado | Motivo |
|---|---|---|
| `ideiaspace/src/main/java/com/catolica/ideiaspace/security/SecurityConfig.java` | Regra `/api/v1/auth/provision` → `hasRole("ADMIN")` antes do `permitAll` de `/api/v1/auth/**` | Vulnerabilidade 2.1 |
| `ideiaspace/src/main/java/com/catolica/ideiaspace/controller/AuthController.java` | `@PreAuthorize("hasRole('ADMIN')")` em `provision` e import correspondente | Segunda barreira da 2.1 |
| `ideiaspace/src/main/java/com/catolica/ideiaspace/config/AppSecurityProperties.java` | Validação de segredos: tamanho mínimo de 32 e recusa de marcadores de exemplo nas três chaves | Item 2.3 |
| `ideiaspace/src/main/resources/application.properties` | Removidos fallbacks de senha e usuário `joaopedro`; flyway passa a usar `postgres` por padrão; URL padrão `localhost:5434/ideiaspace` | Item 2.4 |
| `ideiaspace/src/test/resources/application.properties` | Senhas lidas do ambiente sem fallback; inclui `APP_BOOTSTRAP_ADMIN_*` com credenciais de teste para criar o admin dos testes de integração | Item 2.4 e testes |
| `ideiaspace/src/test/java/com/catolica/ideiaspace/DatabaseRlsSmokeTests.java` | Defaults `joaopedro`/`databasejoaopedro` removidos das `@Value` | Item 2.4 |
| `ideiaspace/src/test/java/com/catolica/ideiaspace/integration/DisciplinaAndAuthIntegrationTest.java` | `test01` agora espera 401 para provisionamento anônimo (antes tentava criar admin sem autenticação) | Compatibilidade com a 2.1 |
| `compose.yaml` | Senhas e segredos substituídos por `${VAR:?...}`; `APP_JWT_SECRET`, `APP_CPF_*`, `APP_CORS_ORIGINS` e `APP_BOOTSTRAP_ADMIN_*` repassados ao backend; `APP_BACKEND_DB_PASSWORD` repassada ao banco | Itens 2.4 e 2.5 |
| `ideiaspace/.gitignore` | Inclui `.env` | Proteção do arquivo real |
| `.idea/workspace.xml` | Removido o bloco `<envs>` com segredos reais | Item 2.6 |

## 4. Arquivos criados

| Caminho | Finalidade |
|---|---|
| `ideiaspace/src/main/java/com/catolica/ideiaspace/config/FirstAdminBootstrap.java` | Bootstrap controlado do primeiro administrador via variáveis de ambiente |
| `ideiaspace/src/test/java/com/catolica/ideiaspace/integration/ProvisionAccessIntegrationTest.java` | Testes de autorização do provisionamento: anônimo (401), ALUNO e DOCENTE tentando criar ADMIN ou ALUNO (403), ADMIN provisionando (201) |
| `ideiaspace/db/init/01_app_backend_password.sh` | Define a senha da role `app_backend` a partir de `APP_BACKEND_DB_PASSWORD` |
| `.env.example` (raiz do backend, ao lado do `compose.yaml`) | Modelo de variáveis de ambiente, sem valores reais |
| `.gitignore` (raiz do backend) | Ignora `.env` e `.env.*` (exceto `.env.example`) e `.idea/workspace.xml` |
| `FASE0_RELATORIO.md` | Este relatório |

## 5. Arquivos removidos

Nenhum arquivo foi removido. Nenhuma migration foi alterada ou excluída. Apenas o bloco `<envs>` de `workspace.xml` foi retirado.

## 6. Configuração do ambiente

### Variáveis criadas ou tornadas obrigatórias

| Variável | Obrigatória | Uso |
|---|---|---|
| `POSTGRES_PASSWORD` | Sim | Superusuário `postgres` (container e Flyway) |
| `APP_BACKEND_DB_PASSWORD` | Sim | Senha da role `app_backend` (runtime do backend e script de init) |
| `APP_JWT_SECRET` | Sim | Assinatura dos tokens (mínimo 32 caracteres, sem marcadores de exemplo) |
| `APP_CPF_KEY` | Sim | Exigida na inicialização (mínimo 32 caracteres) |
| `APP_CPF_HMAC_KEY` | Sim | Exigida na inicialização (mínimo 32 caracteres) |
| `APP_TOKEN_TTL_MINUTES` | Não | Padrão 60 |
| `APP_CORS_ORIGINS` | Não | Padrão `http://localhost:3000` |
| `APP_BOOTSTRAP_ADMIN_NAME`, `_EMAIL`, `_PASSWORD` | Não | Só para criar o primeiro ADMIN (senha com 12+ caracteres) |

Para execução local fora do Docker, as variáveis do Spring são `SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_PASSWORD` e `SPRING_FLYWAY_USER` (padrão `postgres`), além das variáveis `APP_*`. A URL padrão local agora é `jdbc:postgresql://localhost:5434/ideiaspace`.

### Como configurar o `.env`
```bash
cd backend
cp .env.example .env
# gere cada segredo e cole no .env:
openssl rand -hex 32
```
Substitua todos os valores que começam com `troque`. Não versione o `.env`.

### Como iniciar com Docker Compose
```bash
cd backend
docker compose up -d --build
docker compose logs -f backend
```
Em um volume novo, o script `01_app_backend_password.sh` define a senha de `app_backend` na primeira inicialização.

**Volume já existente** (o script de init não roda de novo). Defina a senha manualmente, sem expor o valor no histórico:
```bash
docker compose exec -T db psql -U postgres -d ideiaspace -v pw="$APP_BACKEND_DB_PASSWORD" <<'SQL'
ALTER ROLE app_backend WITH PASSWORD :'pw';
SQL
```

### Como iniciar localmente (sem Docker)
Exporte as variáveis (ou use um plugin de EnvFile na IDE, que lê o `.env`) e rode:
```bash
./mvnw spring-boot:run
```

### Como configurar o primeiro administrador
1. Preencha `APP_BOOTSTRAP_ADMIN_NAME`, `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD` no `.env` (senha com 12+ caracteres).
2. Suba o backend. O log mostra `Administrador inicial provisionado (id=...)`.
3. Faça login em `POST /api/v1/auth/login` e use o token para os próximos provisionamentos.
4. Esvazie ou remova as três variáveis `APP_BOOTSTRAP_ADMIN_*` e reinicie. Se mantidas, o bootstrap apenas registra que o admin já existe.

Exemplo de login e provisionamento (substitua os valores):
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@exemplo.edu.br","senha":"SUA_SENHA"}' | jq -r .token)

curl -X POST http://localhost:8081/api/v1/auth/provision \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nomeCompleto":"Aluno Teste","email":"aluno@exemplo.edu.br","senha":"senha_forte_123","perfil":"ALUNO"}'
```

## 7. Testes executados

| Verificação | Resultado |
|---|---|
| Testes Maven (`./mvnw test`) | **NÃO EXECUTADOS.** O ambiente não consegue acessar o Maven Central, e não há Maven nem PostgreSQL instalados. |
| Compilação (`mvn package`) | **NÃO EXECUTADA** pelo mesmo motivo. |
| Sintaxe dos arquivos Java alterados e novos (`javac -proc:none`) | Sem erros de sintaxe. Os únicos avisos são de símbolos/pacotes ausentes, esperados sem as dependências. Não equivale à compilação. |
| Sintaxe do script `01_app_backend_password.sh` (`bash -n`) | OK |
| Sintaxe do `compose.yaml` (parse YAML) | OK |
| `workspace.xml` (XML bem formado) | OK |
| Busca por credenciais fixas no projeto | Sem senhas ou segredos restantes. Permanecem apenas nomes de usuário em metadados da IDE. |

Testes que precisam ser executados pela equipe com Maven e PostgreSQL:
- `ProvisionAccessIntegrationTest` (6 cenários de autorização do provisionamento).
- `DisciplinaAndAuthIntegrationTest.test01` (agora espera 401).
- `TurmaAndParticipantesIntegrationTest` (depende do admin criado pelo bootstrap de teste).
- `DatabaseRlsSmokeTests` e `SecurityUnitTests` (confirmar que a nova validação de segredos aceita as chaves de teste).

Os testes de integração agora dependem de `SPRING_TEST_DATASOURCE_PASSWORD` e `SPRING_TEST_FLYWAY_PASSWORD` no ambiente, porque o fallback foi removido.

## 8. Pendências identificadas

Problemas encontrados que não foram corrigidos nesta fase:

1. **Rotação obrigatória de segredos (urgente).** O JWT secret (`64fb453c…`), as chaves `APP_CPF_KEY` e `APP_CPF_HMAC_KEY`, e as senhas `app_backend_password` e `postgres_dev_password` estavam em `.idea/workspace.xml` e no arquivo original. Devem ser considerados comprometidos: gere novos valores e troque as senhas no banco. Se o repositório já foi publicado, remover o arquivo não basta; é preciso reescrever o histórico ou tratar os valores como vazados.
2. **CPF não é criptografado.** Em `AuthService.provisionar`, `cpf_cifrado` é `"ENC_" + email` e `cpf_lookup_hmac` é o SHA-256 do e-mail. As chaves `APP_CPF_KEY` e `APP_CPF_HMAC_KEY` são validadas na inicialização, mas **não são usadas em lugar nenhum**. Isso é um problema de privacidade (LGPD). Deve ser corrigido com criptografia real e HMAC com chave, em fase própria, com cuidado com as migrations e com os dados existentes.
3. **`storagePath` controlado pelo cliente.** `ConteudoRequestDTO.storagePath` e `ConteudoUpdateDTO.storagePath` são aceitos e gravados diretamente (`ConteudoService.cadastrar` e `atualizar`). Só há validação de formato (não é URL). Solução prevista para a Fase 2: o servidor gera o caminho (por exemplo, UUID em diretório controlado), o cliente envia apenas metadados, e o campo sai do DTO de entrada. A constraint `ck_conteudo_storage_path_valido` e o índice único devem ser reavaliados nessa fase.
4. **Função de provisionamento no banco ainda aceita ADMIN sem administrador prévio.** `fn_provisionar_usuario_inicial` permite qualquer `perfil` quando não existe ADMIN. Hoje a proteção é a camada HTTP mais o bootstrap. Como defesa em profundidade, convém uma nova migration (não alterar as existentes) que bloqueie ADMIN nessa situação, deixando o bootstrap por outro caminho.
5. **Revogação de token.** O perfil fica no token por até `APP_TOKEN_TTL_MINUTES`. Um usuário rebaixado mantém a role no token até expirar. O provisionamento está protegido porque a função do banco confere `is_admin()` na tabela, mas outras regras baseadas em role do token devem ser revisadas.
6. **Senhas de conta de sistema no histórico de comandos.** O comando de volume existente (seção 6) evita expor a senha, mas o operador deve evitar `ps`/histórico ao usar `-v pw=...`.
7. **Login sem limite de tentativas.** Não há rate limiting em `/api/v1/auth/login`. Fora do escopo desta fase.
8. **Volume do PostgreSQL.** O Compose monta `postgres_data` em `/var/lib/postgresql`. Na imagem `postgres:18`, o diretório de dados mudou para `/var/lib/postgresql/18/docker`. Não foi alterado, mas deve ser verificado antes de qualquer recriação de volume.
9. **Nomes de usuário em metadados da IDE** (`.idea/dataSources*`) ainda aparecem. Não são senhas; podem ser removidos ao ignorar `.idea/` por completo.

## 9. Riscos e observações

- **Quebra de contrato para o frontend:** `POST /api/v1/auth/provision` agora exige `Authorization: Bearer <token de ADMIN>`. Chamadas anônimas recebem 401; tokens de ALUNO ou DOCENTE recebem 403. O login e o formato de resposta não mudaram.
- **Ambiente obrigatório:** a aplicação não sobe sem `APP_JWT_SECRET`, `APP_CPF_KEY`, `APP_CPF_HMAC_KEY`, `SPRING_DATASOURCE_PASSWORD` e `SPRING_FLYWAY_PASSWORD` (localmente) ou sem o `.env` (Docker). Quem já rodava com os fallbacks antigos precisa criar o `.env`.
- **Porta e banco padrão local:** a URL padrão mudou de `localhost:5432/porto-digital` para `localhost:5434/ideiaspace`, alinhada ao Compose.
- **Volume existente:** o script `01_app_backend_password.sh` só roda em volume novo. Em volume existente, execute o comando manual da seção 6.
- **Configuração da IDE:** a configuração de execução "run 2" perdeu as variáveis. Use um plugin de EnvFile apontando para o `.env`, ou exporte as variáveis.
- **Testes de integração:** agora criam o admin pelo bootstrap de teste. Se o banco de teste já tiver um admin com outras credenciais, os testes que fazem login com `admin.integ@ideiaspace.com` falharão, como já acontecia antes.

## 10. Estado final da Fase 0

**PARCIALMENTE CONCLUÍDA.**

Concluído no código:
- Provisionamento restrito a ADMIN autenticado (URL e anotação).
- Bootstrap controlado do primeiro administrador.
- Remoção de credenciais dos arquivos versionados, com variáveis obrigatórias.
- Senha de `app_backend` definida pelo `.env` no Compose.
- Recusa de segredos de exemplo na inicialização.
- `.env` protegido pelo `.gitignore`.

Não concluído:
- Compilação e testes não executados (sem acesso a dependências e a PostgreSQL neste ambiente).
- Segredos expostos ainda precisam ser rotacionados pela equipe.
- Os itens 2 a 4 da seção 8 (CPF, storagePath e função de provisionamento) são pendências de fases seguintes.

## 11. Próximos passos

Fase 1 (sem iniciar a implementação aqui):
1. Rodar `./mvnw test` com PostgreSQL disponível e registrar os resultados.
2. Rotacionar todos os segredos listados na seção 8 e atualizar os ambientes.
3. Planejar a correção de criptografia e HMAC do CPF com as chaves já validadas.
4. Planejar a remoção de `storagePath` do DTO de entrada (Fase 2) e a geração do caminho no servidor.
5. Avaliar a nova migration de defesa em profundidade para `fn_provisionar_usuario_inicial`.
