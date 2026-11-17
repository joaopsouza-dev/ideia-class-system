# Guia de início do Ideia Space
> Este é o guia operacional detalhado para primeiro provisionamento e uso. Para visão geral, tecnologias, comandos de teste e estado atual, comece pelo [README principal](README.md). Os documentos técnicos específicos estão listados no README.

Este guia leva você do zero até o sistema rodando na sua máquina, com um professor de teste cadastrado e com disciplinas e turmas associadas a ele.

Ele foi escrito para quem nunca usou o projeto. Siga os passos na ordem. Quando algo der errado, procure na seção **Problemas comuns**, no fim do documento.

---

## Sumário

1. [O que você vai precisar instalar](#1-o-que-você-vai-precisar-instalar)
2. [Entendendo a estrutura do projeto](#2-entendendo-a-estrutura-do-projeto)
3. [Preparando o arquivo `.env` do backend](#3-preparando-o-arquivo-env-do-backend)
4. [Subindo o banco e o backend com Docker](#4-subindo-o-banco-e-o-backend-com-docker)
5. [Rodando o frontend](#5-rodando-o-frontend)
6. [Criando um professor de teste pelo Postman](#6-criando-um-professor-de-teste-pelo-postman)
7. [Problemas comuns](#7-problemas-comuns)

---

## 1. O que você vai precisar instalar

| Ferramenta | Para quê | Obrigatória? |
|---|---|---|
| **Git** | Baixar o projeto | Sim |
| **Docker Desktop** | Subir o banco PostgreSQL e o backend em containers | Sim |
| **Node.js 22 LTS** (ou 20.19 ou superior) | Rodar o frontend React | Sim |
| **Postman** | Criar o professor de teste e as disciplinas | Sim, para o passo 6 |
| **JDK 21** | Rodar o backend sem Docker | Não, só se for usar a alternativa da seção 4.4 |

> **Maven não precisa ser instalado manualmente.** O projeto traz o `mvnw` (Maven Wrapper), que baixa o Maven sozinho na primeira execução. O Docker também usa Maven internamente para compilar o backend.

### 1.1. Instalando o Git

- **Windows:** baixe em https://git-scm.com/download/win e instale com as opções padrão. Ele também instala o **Git Bash**, um terminal que usaremos nos exemplos do Windows.
- **macOS:** abra o Terminal e digite `git --version`. Se aparecer uma janela pedindo para instalar as ferramentas de linha de comando, aceite. Ou instale com Homebrew (ver 1.4).

### 1.2. Instalando o Docker Desktop

- **Windows:**
  1. Baixe em https://www.docker.com/products/docker-desktop/.
  2. Durante a instalação, mantenha marcada a opção **Use WSL 2 instead of Hyper-V**.
  3. Reinicie o computador se o instalador pedir.
  4. Abra o Docker Desktop, aceite os termos e espere o ícone da baleia ficar **verde** (“Engine running”).
- **macOS:**
  1. Baixe em https://www.docker.com/products/docker-desktop/ e escolha a versão para o seu chip (**Apple Silicon** ou **Intel**). Para descobrir: menu Apple > Sobre este Mac.
  2. Arraste o Docker para a pasta Aplicativos e abra-o.
  3. Espere a baleia no topo da tela ficar parada (“Docker Desktop is running”).

### 1.3. Instalando o Node.js

- **Windows:** baixe o instalador **LTS** em https://nodejs.org e siga as telas padrão.
- **macOS:** instale com Homebrew (ver 1.4) ou com o instalador do site.

Confirme no terminal:

```bash
node --version
npm --version
```

Você deve ver algo como `v22.x.x` e `10.x.x`.

### 1.4. Instalando o Homebrew (somente macOS)

O Homebrew é um gerenciador de programas para macOS. Ele facilita instalar Node e Java. No Terminal:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Ao terminar, o próprio instalador mostra dois comandos com `echo` e `eval`. Copie e execute-os.

Depois instale o que for necessário:

```bash
brew install node
brew install --cask docker   # só se preferir instalar o Docker pelo terminal
```

### 1.5. Instalando o Postman

Baixe em https://www.postman.com/downloads/ e crie uma conta gratuita, se pedido. Não é preciso login para usar o app localmente.

---

## 2. Entendendo a estrutura do projeto

Depois de baixar o repositório, a pasta principal tem esta organização:

```text
porto-digital/
├── backend/
│   ├── .env.example          ← modelo das variáveis do backend
│   ├── .env                  ← SEU arquivo real (você cria; nunca versione)
│   ├── compose.yaml          ← sobe o banco e o backend com Docker
│   └── ideiaspace/           ← código Spring Boot (pom.xml, mvnw, Dockerfile)
├── frontend/
│   ├── .env.example          ← modelo das variáveis do frontend
│   ├── .env                  ← SEU arquivo real (você cria se não existir)
│   ├── package.json
│   └── src/
└── docs/
```

Portas que o sistema usa na sua máquina:

| Serviço | Endereço | Observação |
|---|---|---|
| Frontend (React) | http://localhost:5173 | Criado pelo `npm run dev` |
| Backend (Spring Boot) | http://localhost:8081 | Mapeado pelo Docker |
| Banco (PostgreSQL) | localhost:5434 | Mapeado pelo Docker (dentro do container é 5432) |

Se alguma dessas portas já estiver ocupada por outro programa, o passo não funciona. Veja a seção de problemas comuns.

---

## 3. Preparando o arquivo `.env` do backend

O arquivo `.env` guarda senhas e chaves. **Ele não vai para o Git** (já está no `.gitignore`), por isso cada desenvolvedor cria o seu.

### 3.1. Copiando o modelo

Abra o terminal **dentro da pasta `backend`**.

- **macOS:**
  ```bash
  cp .env.example .env
  ```
- **Windows (Git Bash):**
  ```bash
  cp .env.example .env
  ```
- **Windows (PowerShell):**
  ```powershell
  Copy-Item .env.example .env
  ```

### 3.2. Gerando as chaves secretas

O backend se recusa a iniciar se essas chaves estiverem vazias, tiverem menos de 32 caracteres ou ainda contiverem o texto “troque”. Você precisa gerar **três** chaves: `APP_JWT_SECRET`, `APP_CPF_KEY` e `APP_CPF_HMAC_KEY`.

- **macOS** (Terminal) ou **Windows** (Git Bash). Rode o comando três vezes, uma para cada chave:
  ```bash
  openssl rand -hex 32
  ```
- **Windows** (PowerShell), sem openssl, use este comando. Ele gera uma chave a cada execução:
  ```powershell
  $b = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString("x2") })
  ```

Cada comando imprime uma linha de 64 caracteres. Copie cada uma para o campo certo.

### 3.3. Preenchendo o `.env`

Abra `backend/.env` em qualquer editor de texto (VS Code, Bloco de Notas, TextEdit) e preencha:

```dotenv
# Senha do superusuário do PostgreSQL (e usada pelas migrations do Flyway)
POSTGRES_PASSWORD=escolha_uma_senha_forte_para_o_postgres

# Senha da role app_backend, usada pelo backend para acessar o banco
APP_BACKEND_DB_PASSWORD=escolha_outra_senha_forte_para_o_backend

# Chaves geradas no passo 3.2 (cada uma com 64 caracteres)
APP_JWT_SECRET=cole_a_primeira_chave_aqui
APP_CPF_KEY=cole_a_segunda_chave_aqui
APP_CPF_HMAC_KEY=cole_a_terceira_chave_aqui

APP_TOKEN_TTL_MINUTES=60
APP_CORS_ORIGINS=http://localhost:5173,http://localhost:3000

# Primeiro administrador. Preencha SOMENTE na primeira vez.
APP_BOOTSTRAP_ADMIN_NAME=Seu Nome
APP_BOOTSTRAP_ADMIN_EMAIL=admin@ideiaspace.com
APP_BOOTSTRAP_ADMIN_PASSWORD=uma_senha_com_pelo_menos_12_caracteres
```

Pontos de atenção:

- **As três variáveis de bootstrap do admin devem ser preenchidas juntas.** Se preencher uma, precisa preencher as três.
- A senha do admin precisa ter **no mínimo 12 caracteres**.
- O admin é criado **somente se o banco ainda não tiver nenhum administrador**. Depois da primeira execução, você pode deixar essas três linhas vazias.
- Não use aspas nem espaços ao redor do `=`.

> **Importante:** a senha de `POSTGRES_PASSWORD` e `APP_BACKEND_DB_PASSWORD` é usada na **primeira criação do banco**. Se você mudar essas senhas depois, veja a seção "Trocar senhas do banco" em Problemas comuns.

---

## 4. Subindo o banco e o backend com Docker

### 4.1. Confirmando que o Docker está rodando

Abra o Docker Desktop e confirme que ele está ativo (ícone verde ou “running”). Depois, no terminal:

```bash
docker --version
docker compose version
```

Os dois precisam responder com uma versão. Se disserem “command not found” ou algo parecido, reinicie o terminal ou o computador.

### 4.2. Subindo os containers

Abra o terminal **dentro da pasta `backend`** (onde está o `compose.yaml`) e rode:

```bash
docker compose up -d --build
```

O que acontece:

1. O Docker baixa a imagem do PostgreSQL 18.6, se ainda não tiver.
2. Cria o banco `ideiaspace` e a role `app_backend`.
3. Espera o banco ficar saudável (pode levar uns 10 segundos).
4. Compila o backend dentro de um container com Maven e Java 21. **A primeira vez demora bastante** (vários minutos), porque baixa dependências.
5. Sobe o backend na porta 8081.

Acompanhe os logs para ver se tudo subiu:

```bash
docker compose logs -f backend
```

Procure por uma linha parecida com `Started IdeiaspaceApplication`. Para sair do log, pressione `Ctrl + C`. Isso **não** derruba o backend.

Para conferir se o backend está respondendo, abra no navegador ou no terminal:

```bash
curl -i http://localhost:8081/api/v1/disciplinas
```

Uma resposta `401` (não autenticado) é **sinal de sucesso**: significa que o backend está no ar e exige login.

### 4.3. Comandos úteis do Docker

| Ação | Comando (rodar em `backend/`) |
|---|---|
| Ver containers ativos | `docker compose ps` |
| Parar tudo (mantém os dados) | `docker compose down` |
| Reiniciar só o backend (depois de mudar código) | `docker compose up -d --build backend` |
| Apagar tudo, inclusive o banco | `docker compose down -v` (**apaga todos os dados**) |

### 4.4. Alternativa: rodar o backend sem Docker (opcional)

Use esta opção só se quiser depurar o backend pela IDE (IntelliJ, por exemplo). Mantenha o banco no Docker e rode o backend na sua máquina:

1. Suba só o banco:
   ```bash
   docker compose up -d db
   ```
2. Instale o **JDK 21**:
   - macOS: `brew install openjdk@21`
   - Windows: baixe o “Temurin 21” em https://adoptium.net
3. Entre na pasta do código e rode o Maven Wrapper:
   - macOS:
     ```bash
     cd ideiaspace
     ./mvnw spring-boot:run
     ```
   - Windows (PowerShell ou Prompt de Comando):
     ```powershell
     cd ideiaspace
     .\mvnw.cmd spring-boot:run
     ```
   Na primeira vez, o `mvnw` baixa o Maven 3.9.16 sozinho.

> **Atenção:** nesta alternativa, o Spring não lê o `backend/.env` automaticamente. Você precisa exportar as variáveis no terminal antes de rodar (ou configurar a IDE para lê-las). Sem isso, a aplicação não inicia. Para a maioria das pessoas, o caminho com Docker (4.2) é o mais simples.

---

## 5. Rodando o frontend

### 5.1. Configurando o `.env` do frontend

Entre na pasta `frontend` e confira se existe o arquivo `.env`:

- **macOS / Windows (Git Bash):**
  ```bash
  cd frontend
  ls -la .env || cp .env.example .env
  ```
- **Windows (PowerShell):**
  ```powershell
  cd frontend
  if (-not (Test-Path .env)) { Copy-Item .env.example .env }
  ```

O conteúdo precisa ser este:

```dotenv
VITE_API_BASE_URL=http://localhost:8081
VITE_USE_MOCKS=false
VITE_MAX_PDF_MB=50
VITE_MAX_VIDEO_MB=500
```

`VITE_USE_MOCKS=false` é o que faz o frontend usar o backend real. Se estiver `true`, o sistema mostra dados falsos e nada do que você fizer no Postman vai aparecer.

### 5.2. Instalando as dependências

Dentro de `frontend/`:

```bash
npm install
```

Isso baixa as bibliotecas do projeto para a pasta `node_modules`. Demora um pouco na primeira vez. Não precisa rodar de novo, a menos que o `package.json` tenha mudado.

### 5.3. Iniciando o servidor de desenvolvimento

```bash
npm run dev
```

O terminal mostra algo como `Local: http://localhost:5173/`. Abra esse endereço no navegador.

Para parar, pressione `Ctrl + C` no terminal.

### 5.4. Conferindo o build (opcional, mas recomendado)

```bash
npm run build
```

Se terminar sem erros, o código do frontend está íntegro.

---

## 6. Criando um professor de teste pelo Postman

Nesta seção, você vai:

1. Fazer login como administrador e obter o token.
2. Criar um usuário professor.
3. Criar uma disciplina.
4. Criar uma turma dessa disciplina.
5. Vincular o professor à turma como docente.

> **Como o sistema funciona:** a disciplina não tem professor direto. O professor é vinculado a uma **turma** com o papel `DOCENTE`, e a turma pertence a uma disciplina. Por isso o passo 5 é o que faz as disciplinas do professor aparecerem para ele.

### 6.1. Configurando o Postman

1. Abra o Postman e crie uma **Collection** chamada `Ideia Space`.
2. Clique nos três pontinhos da collection > **Edit** > aba **Variables**. Crie uma variável:
   - Variable: `baseUrl`, Initial value: `http://localhost:8081`
   - Variable: `token` (deixe vazio por enquanto)
3. Salve.

Nas requisições abaixo, use `{{baseUrl}}` no endereço. Assim, se a porta mudar, você altera só em um lugar.

### 6.2. Passo 1: login do administrador (obter o token)

Crie uma nova requisição:

- **Método:** `POST`
- **URL:** `{{baseUrl}}/api/v1/auth/login`
- **Aba Headers:** `Content-Type: application/json` (o Postman costuma colocar sozinho)
- **Aba Body** > **raw** > **JSON**:

```json
{
  "email": "admin@ideiaspace.com",
  "senha": "a_senha_que_voce_colocou_no_APP_BOOTSTRAP_ADMIN_PASSWORD"
}
```

Clique em **Send**. A resposta deve ser `200 OK` com este formato:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "usuario": {
    "id": 1,
    "nomeCompleto": "Seu Nome",
    "email": "admin@ideiaspace.com",
    "perfil": "ADMIN"
  }
}
```

**Copie o valor de `token`** (sem aspas). Para salvar na variável:

1. Clique na aba **Scripts** > **Post-response** da requisição de login.
2. Cole:
   ```javascript
   pm.collectionVariables.set("token", pm.response.json().token);
   ```
3. Envie o login de novo. A partir de agora, o token fica guardado na variável `token`.

> **Validade:** o token dura 60 minutos (`APP_TOKEN_TTL_MINUTES`). Se as requisições começarem a responder `401`, basta enviar o login de novo.

### 6.3. Configurando a autenticação nas próximas requisições

Para as requisições que exigem login (todas, exceto `/auth/login`):

- Aba **Authorization** > **Type: Bearer Token** > **Token: `{{token}}`**

Você pode fazer isso em cada requisição. Ou, para não repetir, aba **Authorization** da própria collection > **Bearer Token** > `{{token}}`. Assim todas as requisições da collection herdam o token.

### 6.4. Passo 2: criar o professor

Nova requisição:

- **Método:** `POST`
- **URL:** `{{baseUrl}}/api/v1/auth/provision`
- **Authorization:** herdada (Bearer `{{token}}`)
- **Body** > **raw** > **JSON**:

Escolha uma senha forte para a conta local. O valor no exemplo abaixo é apenas um marcador, não uma credencial válida.

```json
{
  "nomeCompleto": "Professor Teste",
  "email": "professor.teste@ideiaspace.com",
  "senha": "<SENHA_FORTE_ESCOLHIDA_LOCALMENTE>",
  "perfil": "DOCENTE"
}
```

Resposta esperada: `201 Created`, com algo como:

```json
{
  "id": 2,
  "mensagem": "Usuário DOCENTE provisionado com sucesso."
}
```

**Anote o `id`.** Ele é o `usuarioId` que você vai usar no passo 6.7.

Regras importantes:

- Só o **ADMIN** pode criar usuários por esta rota. Se você enviar com o token de um professor ou aluno, a resposta será `403 Forbidden`.
- Os perfis aceitos são `ADMIN`, `DOCENTE` e `ALUNO`. Use `DOCENTE` para o professor.
- A senha é enviada em texto puro apenas nesta requisição; o backend guarda apenas o hash.

**Teste de login do professor (recomendado):** envie um `POST` para `{{baseUrl}}/api/v1/auth/login` com o e-mail e a senha do professor. Deve retornar `200` com `"perfil": "DOCENTE"`. Confirmar isso antes evita confusão depois.

### 6.5. Passo 3: criar a disciplina

- **Método:** `POST`
- **URL:** `{{baseUrl}}/api/v1/disciplinas`
- **Body** > **raw** > **JSON**:

```json
{
  "codigo": "TS-TESTE",
  "nome": "Teste de Software",
  "descricao": "Disciplina de teste criada pelo Postman"
}
```

Resposta esperada: `201 Created` (ou `200 OK`), com o objeto da disciplina criada, incluindo um campo `id`.

**Anote o `id` da disciplina.** Ele será usado no passo 6.6.

Se você tentar criar com um `codigo` que já existe, o banco vai recusar. Nesse caso, troque o código (por exemplo, `TS-TESTE-2`).

### 6.6. Passo 4: criar a turma

- **Método:** `POST`
- **URL:** `{{baseUrl}}/api/v1/turmas`
- **Body** > **raw** > **JSON**:

```json
{
  "disciplinaId": 1,
  "codigo": "TS-A",
  "nome": "Turma A",
  "ano": 2026,
  "semestre": 2,
  "status": "ATIVA"
}
```

Troque `disciplinaId` pelo `id` anotado no passo 6.5.

Regras:

- `ano` precisa estar entre 2000 e 2100.
- `semestre` precisa ser `1` ou `2`.
- `status` aceita `ATIVA`, `INATIVA` ou `CONCLUIDA`.
- `nome` precisa ter entre 2 e 255 caracteres.

Resposta esperada: o objeto da turma criada, com um campo `id`. **Anote o `id` da turma.**

### 6.7. Passo 5: vincular o professor à turma

- **Método:** `POST`
- **URL:** `{{baseUrl}}/api/v1/turmas/{turmaId}/participantes`

Troque `{turmaId}` pelo `id` da turma, por exemplo `.../api/v1/turmas/1/participantes`.

- **Body** > **raw** > **JSON**:

```json
{
  "usuarioId": 2,
  "papel": "DOCENTE",
  "inicioEm": "2026-08-01",
  "fimEm": null
}
```

- `usuarioId`: o `id` do professor anotado no passo 6.4.
- `papel`: **`DOCENTE`** para o professor. O outro valor possível é `DISCENTE` (aluno).
- `inicioEm` e `fimEm` são opcionais.

Resposta esperada: `201 Created` ou `200 OK`, com o vínculo criado.

Para vincular o professor a mais de uma turma (ou a mais de uma disciplina), repita os passos 6.5 a 6.7 com outros nomes e códigos.

### 6.8. Passo 6: conferir tudo

Envie estas requisições `GET` (todas com o token de admin):

| O que conferir | URL |
|---|---|
| Disciplinas | `{{baseUrl}}/api/v1/disciplinas` |
| Turmas | `{{baseUrl}}/api/v1/turmas` |
| Participantes da turma | `{{baseUrl}}/api/v1/turmas/{turmaId}/participantes` |

No último, confirme que aparece o professor com `papel: DOCENTE`.

### 6.9. Conferindo no sistema

1. Abra o frontend em http://localhost:5173.
2. Faça login com `professor.teste@ideiaspace.com` e a senha definida no passo 6.4.
3. O professor deve ver a disciplina e a turma criadas.

Se não aparecer nada, veja a seção de problemas comuns.

### 6.10. Resumo da ordem

1. Login admin → copiar `token`
2. `POST /auth/provision` → anotar `id` do professor
3. `POST /disciplinas` → anotar `id` da disciplina
4. `POST /turmas` (usando `disciplinaId`) → anotar `id` da turma
5. `POST /turmas/{turmaId}/participantes` (usando `usuarioId` e `papel: DOCENTE`)

---

## 7. Problemas comuns

### "Defina POSTGRES_PASSWORD no arquivo .env" (ou outra variável)

O Docker não encontrou a variável. Confirme que o arquivo se chama exatamente `.env` (não `.env.txt`) e está em `backend/`, ao lado do `compose.yaml`. No Windows, o Bloco de Notas às vezes salva como `.env.txt`: ative a opção de mostrar extensões de arquivo para conferir.

### O backend sobe e para logo com erro de chave

Uma das chaves (`APP_JWT_SECRET`, `APP_CPF_KEY`, `APP_CPF_HMAC_KEY`) está vazia, curta ou contém a palavra “troque”. Gere novas com o passo 3.2 e rode `docker compose up -d backend` de novo.

### Erro de porta já em uso (`port is already allocated`, `address already in use`)

Outro programa está usando a 5434, a 8081 ou a 5173.

- Feche o programa que está usando a porta.
- Ou troque a porta no `compose.yaml` (lado esquerdo do `:`) e no `VITE_API_BASE_URL` do frontend.

### O Docker diz que não consegue se conectar ao daemon

O Docker Desktop não está aberto. Abra-o e espere ficar pronto.

### `401 Unauthorized` nas requisições do Postman

- Você esqueceu o token ou ele expirou (60 min). Envie o login de novo.
- Confira se a aba Authorization está como **Bearer Token** com `{{token}}`.

### `403 Forbidden` no provision

O token usado não é de um ADMIN. Faça login com a conta de administrador do passo 6.2.

### Não existe admin no banco e o bootstrap não criou

O bootstrap roda na inicialização e só cria o admin se o banco não tiver nenhum. Confira no log:

```bash
docker compose logs backend | grep -i bootstrap
```

Se o banco já tinha outro admin de testes, ele não será recriado. Nesse caso, use o admin que já existe, ou apague o volume (ver abaixo).

### Trocar senhas do banco ou volume antigo

O script `01_app_backend_password.sh` roda **somente na primeira criação do volume**. Se você mudou `APP_BACKEND_DB_PASSWORD` depois, o backend não vai conseguir conectar. Duas saídas:

- **Recomeçar do zero** (apaga todos os dados locais): em `backend/`, rode `docker compose down -v` e depois `docker compose up -d --build`.
- **Manter os dados:** conecte no banco e altere a senha manualmente. Consulte `FASE0_RELATORIO.md`, na pasta `backend`.

### A tela do frontend mostra dados que não existem no Postman

Confira se `VITE_USE_MOCKS=false` no `frontend/.env`. Depois de mudar o `.env`, pare o `npm run dev` (`Ctrl + C`) e rode de novo.

### Erro de CORS no navegador

A origem do frontend precisa estar em `APP_CORS_ORIGINS`. O padrão inclui `http://localhost:5173`. Se você abriu o frontend por outra porta, adicione-a no `backend/.env` e reinicie o backend com `docker compose up -d backend`.

### Testes de integração do frontend

O comando `npm run test:integration` (dentro de `frontend/`) só funciona com o backend rodando e com as contas de teste existentes. Ele não cria essas contas sozinho. Antes de rodar, confirme que o backend responde em http://localhost:8081.

---

## Checklist final

- [ ] Git, Docker Desktop, Node.js 22 LTS e Postman instalados
- [ ] Docker Desktop aberto e ativo
- [ ] `backend/.env` criado e preenchido (senhas, três chaves, admin de bootstrap)
- [ ] `docker compose up -d --build` executado em `backend/`, com o backend respondendo na 8081
- [ ] `frontend/.env` com `VITE_USE_MOCKS=false`
- [ ] `npm install` e `npm run dev` rodando, com o site aberto em http://localhost:5173
- [ ] Login do admin no Postman e token copiado
- [ ] Professor criado com `perfil: DOCENTE`
- [ ] Disciplina e turma criadas
- [ ] Professor vinculado à turma com `papel: DOCENTE`
- [ ] Login do professor no frontend mostrando a turma e a disciplina
