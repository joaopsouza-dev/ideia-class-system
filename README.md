# Repositório Digital de Conteúdo Acadêmico

Sistema web para gerenciamento, organização e disponibilização protegida de conteúdos acadêmicos, como livros, apostilas e videoaulas.

O projeto tem como objetivo criar uma plataforma na qual conteúdos possam ser organizados por disciplinas e turmas, permitindo que usuários autorizados tenham acesso aos materiais de acordo com seu perfil e vínculo acadêmico.

Além da disponibilização dos conteúdos, o sistema possui como um de seus principais focos a **proteção e rastreabilidade dos materiais**, buscando dificultar sua extração e registrar os acessos realizados.

> **Importante:** nenhuma aplicação web consegue impedir completamente a cópia de um conteúdo que foi disponibilizado para visualização. Por isso, o projeto trabalha com múltiplas camadas de proteção, buscando dificultar a extração, controlar o acesso e possibilitar a identificação dos usuários.

---

## 📌 Objetivos

* Centralizar conteúdos acadêmicos em uma única plataforma.
* Organizar materiais por disciplinas e turmas.
* Permitir diferentes níveis de acesso de acordo com o perfil do usuário.
* Disponibilizar documentos para visualização diretamente na plataforma.
* Permitir reprodução de videoaulas por streaming.
* Dificultar o download e a extração dos conteúdos.
* Aplicar identificação visual aos materiais por meio de marca d'água.
* Registrar acessos e ações relacionadas aos conteúdos.
* Desenvolver uma arquitetura preparada para futuras evoluções do sistema.

---

## 🚀 Funcionalidades

### 👤 Usuários

* Cadastro de usuários.
* Autenticação.
* Gerenciamento de perfis.
* Controle de permissões.
* Associação de usuários às turmas.
* Definição de papel do usuário dentro de uma turma.

### 🎓 Estrutura acadêmica

* Cadastro de disciplinas.
* Cadastro de turmas.
* Associação entre disciplinas e turmas.
* Associação entre usuários e turmas.
* Identificação de usuários como docentes ou discentes em cada turma.

### 📚 Conteúdos

A plataforma poderá trabalhar com diferentes tipos de materiais:

* Livros em PDF.
* Apostilas em PDF.
* Videoaulas.
* Outros materiais acadêmicos que possam ser incorporados futuramente.

Cada conteúdo possui informações como:

* título;
* descrição;
* tipo;
* turma relacionada;
* professor responsável;
* localização do arquivo;
* tamanho;
* data de envio.

### 📄 Visualização de documentos

Os documentos serão disponibilizados por meio de um visualizador integrado à aplicação.

Entre as medidas previstas estão:

* visualização dentro da plataforma;
* ausência de acesso público direto ao arquivo original;
* controle de autorização pelo backend;
* marca d'água identificando o usuário;
* mecanismos para dificultar cópia e extração;
* possibilidade de registro das ações realizadas durante o acesso.

### 🎥 Videoaulas

As videoaulas serão disponibilizadas por meio de streaming.

A arquitetura prevê a utilização de tecnologias como HLS para segmentação da transmissão, evitando a disponibilização direta de um único arquivo de vídeo público.

Também poderão ser utilizados:

* URLs de acesso temporário;
* controle de autorização;
* marca d'água;
* registro de acessos;
* mecanismos adicionais de proteção.

### 🔐 Segurança e rastreabilidade

A segurança dos conteúdos será baseada em diferentes camadas:

```text
Autenticação
     ↓
Autorização
     ↓
Validação do vínculo com a turma
     ↓
Controle de acesso ao conteúdo
     ↓
Visualização protegida
     ↓
Marca d'água
     ↓
Registro de acesso
```

O objetivo não é prometer proteção absoluta, mas aumentar a dificuldade de extração e possibilitar a rastreabilidade dos acessos.

---

# 🏗️ Arquitetura

A aplicação segue uma arquitetura organizada em camadas, separando responsabilidades entre frontend, backend e banco de dados.

Fluxo principal:

```text
┌───────────────┐
│    Frontend   │
└───────┬───────┘
        │
        │ HTTP / REST
        ↓
┌───────────────┐
│    Backend    │
│  Spring Boot  │
└───────┬───────┘
        │
        ↓
┌───────────────┐
│  PostgreSQL   │
└───────────────┘
```

No backend, a estrutura segue o princípio:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

### Controller

Responsável por receber as requisições HTTP e disponibilizar os endpoints da API.

### Service

Responsável pelas regras de negócio e pelo processamento das operações.

### Repository

Responsável pela comunicação com o banco de dados utilizando Spring Data JPA.

### Model

Contém as entidades que representam os principais objetos do sistema.

### DTO

Responsável pela transferência de dados entre as diferentes camadas da aplicação, evitando que as entidades sejam necessariamente expostas diretamente pela API.

### Exception

Centraliza exceções e tratamentos de erros da aplicação.

### Config

Concentra configurações técnicas do sistema, como segurança, CORS e outros componentes necessários.

---

# 🗂️ Estrutura do projeto

A estrutura geral do repositório está organizada da seguinte maneira:

```text
projeto/
│
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/
│   │       │       └── .../
│   │       │           ├── config/
│   │       │           ├── controller/
│   │       │           ├── dto/
│   │       │           ├── exception/
│   │       │           ├── model/
│   │       │           ├── repository/
│   │       │           └── service/
│   │       │
│   │       └── resources/
│   │
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   ├── public/
│   └── ...
│
├── database/
│   ├── scripts/
│   └── README.md
│
├── docs/
│   ├── architecture/
│   ├── diagrams/
│   └── decisions/
│
├── .gitignore
└── README.md
```

A estrutura pode ser modificada conforme o projeto evoluir.

---

# 🧩 Modelo de dados

A estrutura inicial do banco é baseada nos principais elementos acadêmicos da aplicação.

Relacionamento conceitual:

```text
Disciplina
    │
    │ 1:N
    ↓
Turma
    │
    │ N:N
    ↓
TurmaUsuario
    │
    │ N:1
    ↓
Usuario
```

Os conteúdos são associados às turmas:

```text
Turma
   │
   │ 1:N
   ↓
Conteudo
```

E os acessos aos conteúdos são registrados:

```text
Usuario ───────┐
               │
               ↓
           LogAcesso
               ↑
               │
Conteudo ──────┘
```

### Principais entidades

#### Usuario

Representa uma pessoa cadastrada na plataforma.

Exemplos de informações:

* identificador;
* nome;
* e-mail;
* CPF;
* senha armazenada de forma segura;
* perfil;
* status;
* data de cadastro.

#### Disciplina

Representa uma disciplina acadêmica.

Exemplos:

* código;
* nome;
* descrição.

#### Turma

Representa uma turma vinculada a uma disciplina.

Exemplos:

* disciplina;
* nome;
* semestre/ano.

#### TurmaUsuario

Representa o vínculo entre um usuário e uma turma.

Além do vínculo, armazena o papel exercido pelo usuário naquela turma.

Possíveis papéis:

```text
DOCENTE
DISCENTE
```

Essa entidade é diferente do perfil geral do usuário.

Por exemplo, o perfil pode indicar que determinada pessoa possui uma conta de aluno, enquanto o vínculo com uma turma determina seu papel naquela turma.

#### Conteudo

Representa um material acadêmico disponibilizado na plataforma.

Tipos previstos:

```text
PDF_LIVRO
PDF_APOSTILA
VIDEO_AULA
```

O banco não precisa armazenar necessariamente o arquivo físico. A proposta é manter os metadados e uma referência para o local de armazenamento.

#### LogAcesso

Registra ações relacionadas ao acesso aos conteúdos.

Exemplos:

```text
ABRIU_PDF
ASSISTIU_VIDEO
TENTATIVA_PRINT_DETECTADA
```

Também podem ser armazenadas informações como:

* usuário;
* conteúdo;
* IP;
* navegador/dispositivo;
* data e hora.

---

# 🔐 Arquitetura de proteção dos conteúdos

A proteção dos conteúdos é um dos principais aspectos do projeto.

## Princípio básico

Os arquivos originais não devem ficar disponíveis por URLs públicas.

Em vez disso:

```text
Usuário
   ↓
Frontend
   ↓
Backend
   ↓
Verificação de autorização
   ↓
Conteúdo liberado temporariamente
   ↓
Visualizador / Player
```

O backend deve verificar se o usuário possui permissão para acessar determinado conteúdo antes de disponibilizá-lo.

---

## 📄 Proteção de PDFs

A visualização dos PDFs poderá utilizar PDF.js.

Fluxo:

```text
Usuário
   ↓
Solicita conteúdo
   ↓
Backend verifica autorização
   ↓
Acesso autorizado
   ↓
PDF disponibilizado para visualização
   ↓
PDF.js renderiza o documento
```

Camadas adicionais:

* marca d'água;
* restrição de acesso direto ao arquivo original;
* controle de permissões;
* registros de acesso;
* mecanismos para dificultar cópia;
* bloqueios de determinadas ações no frontend.

> Essas medidas dificultam a extração, mas não tornam tecnicamente impossível capturar o conteúdo.

---

## 🎥 Proteção de videoaulas

As videoaulas poderão utilizar HLS para transmissão segmentada.

Fluxo:

```text
Vídeo original
      ↓
Processamento
      ↓
HLS
      ↓
Playlist
      ↓
Segmentos
      ↓
Player
```

O acesso aos arquivos de vídeo deve ser controlado pelo backend e, em uma evolução da infraestrutura, poderá utilizar mecanismos como URLs ou cookies assinados e temporários.

---

## 🏷️ Marca d'água

Os conteúdos poderão apresentar informações do usuário durante a visualização.

Exemplo:

```text
Nome do usuário
E-mail
Identificador
Data/hora
```

A marca d'água possui duas funções principais:

1. dificultar o compartilhamento não autorizado;
2. permitir a identificação do usuário caso uma captura do conteúdo seja compartilhada.

---

# 📊 Rastreabilidade

O sistema deverá registrar o acesso aos conteúdos.

Exemplo:

```text
Usuário: usuário autorizado
Conteúdo: Apostila de Banco de Dados
Ação: ABRIU_PDF
Data/Hora: 30/09/2026 10:43
```

Esses registros permitem acompanhar o uso dos materiais e podem auxiliar na investigação de acessos indevidos.

---

# 🛠️ Tecnologias

## Backend

* Java
* Spring Boot
* Spring Web
* Spring Data JPA
* Hibernate
* Maven

## Frontend

* HTML
* CSS
* JavaScript
* React, quando aplicável à implementação

## Banco de dados

* PostgreSQL

## Conteúdo

* PDF.js para visualização de documentos
* HLS para transmissão de videoaulas

## Versionamento

* Git
* GitHub

## Documentação

* Markdown
* Mermaid
* Diagramas de arquitetura e banco de dados

---

# 🔄 Fluxo de uma requisição

Um exemplo de funcionamento da aplicação:

```text
1. Usuário acessa o frontend
            ↓
2. Frontend envia uma requisição HTTP
            ↓
3. Controller recebe a requisição
            ↓
4. Service aplica as regras de negócio
            ↓
5. Repository consulta o banco
            ↓
6. PostgreSQL retorna os dados
            ↓
7. Service processa os dados
            ↓
8. Controller retorna a resposta
            ↓
9. Frontend apresenta o resultado
```

---

# 🧪 Testes

O projeto deverá possuir testes para verificar o funcionamento das principais partes da aplicação.

Entre os testes previstos:

* testes das entidades;
* testes dos repositories;
* testes dos services;
* testes dos controllers;
* testes dos endpoints;
* testes de integração;
* testes de autorização;
* testes relacionados ao acesso aos conteúdos.

---

# 🌿 Git e fluxo de desenvolvimento

O projeto utiliza Git para controle de versão.

Recomenda-se trabalhar com branches separadas para cada funcionalidade ou tarefa:

```text
main
 │
 ├── feature/frontend-aluno
 ├── feature/backend-turmas
 ├── feature/database-estrutura
 └── feature/documentacao-arquitetura
```

Após a implementação:

```text
Branch
   ↓
Commit
   ↓
Push
   ↓
Pull Request
   ↓
Code Review
   ↓
Merge
```

---

# 📋 Organização do desenvolvimento

O projeto utiliza uma abordagem baseada em Sprints.

O planejamento geral é separado do acompanhamento detalhado das tarefas.

### Scrum

Utilizado para acompanhar:

* Product Backlog;
* Sprints;
* objetivos das Sprints;
* andamento geral do projeto;
* retrospectivas.

### Kanban

Utilizado para acompanhar as tarefas dentro de cada Sprint:

```text
Backlog
   ↓
A Fazer
   ↓
Em Desenvolvimento
   ↓
Revisão / Code Review
   ↓
Em Teste
   ↓
Concluído
```

---

# 📦 Product Backlog

As principais áreas funcionais do sistema são:

* **PB01 — Gestão de usuários**
* **PB02 — Autenticação e acesso**
* **PB03 — Organização acadêmica**
* **PB04 — Gerenciamento de materiais**
* **PB05 — Acesso aos materiais**
* **PB06 — Visualização de documentos**
* **PB07 — Disponibilização de videoaulas**
* **PB08 — Proteção dos conteúdos**
* **PB09 — Rastreabilidade e segurança**
* **PB10 — Interface e experiência do usuário**

Esses itens representam áreas de negócio do sistema e podem ser detalhados em tarefas técnicas durante as Sprints.

---

# 🧭 Roadmap

O desenvolvimento pode evoluir gradualmente:

## Sprint 1 — Estrutura inicial

* definição da arquitetura;
* organização do repositório;
* criação da estrutura inicial do backend;
* definição inicial do banco;
* protótipos das interfaces;
* definição das responsabilidades;
* configuração do ambiente de desenvolvimento.

## Sprint 2 — Primeira integração

* desenvolvimento das primeiras funcionalidades do backend;
* implementação inicial do banco;
* desenvolvimento das áreas principais do frontend;
* criação dos primeiros endpoints;
* integração:

```text
Frontend
   ↓
Backend
   ↓
PostgreSQL
   ↓
Backend
   ↓
Frontend
```

## Próximas Sprints

Evolução progressiva de:

* autenticação;
* gerenciamento de usuários;
* disciplinas;
* turmas;
* conteúdos;
* visualização de PDFs;
* videoaulas;
* marca d'água;
* proteção;
* rastreabilidade;
* testes;
* refinamento da interface.

---

# ⚠️ Limitações de segurança

O projeto reconhece que **não é possível garantir proteção absoluta de um conteúdo exibido em um dispositivo controlado pelo usuário**.

Mesmo com mecanismos de proteção, um usuário pode utilizar métodos externos para capturar o conteúdo.

Por isso, a estratégia adotada é baseada em múltiplas camadas:

```text
Controle de acesso
        +
Arquivos privados
        +
Acesso temporário
        +
Visualização protegida
        +
Marca d'água
        +
Rastreabilidade
        +
Mecanismos de dificultação
```

O objetivo é aumentar a segurança do conteúdo sem apresentar mecanismos de proteção como uma garantia absoluta contra cópia.

---

# 📄 Licença

Este projeto é destinado a fins educacionais e de desenvolvimento.

A definição da licença de distribuição deverá ser realizada de acordo com as necessidades e regras estabelecidas para o projeto.

---

# 👥 Contribuição

Para contribuir com o projeto:

1. Crie uma branch para sua alteração.
2. Desenvolva a funcionalidade.
3. Faça commits pequenos e descritivos.
4. Execute os testes disponíveis.
5. Envie a branch para o repositório.
6. Abra um Pull Request.
7. Aguarde a revisão.
8. Após aprovação, realize o merge conforme o fluxo definido pela equipe.

---

# 📚 Documentação

A documentação complementar do projeto está localizada no diretório:

```text
docs/
```

Esse diretório poderá conter:

* diagramas;
* arquitetura;
* modelo de banco;
* decisões técnicas;
* documentação de segurança;
* documentação de APIs;
* registros das Sprints.

---

# 🚧 Status

**Em desenvolvimento.**

O sistema está sendo desenvolvido de forma incremental, priorizando inicialmente a construção da arquitetura e a integração entre frontend, backend e banco de dados antes da implementação das funcionalidades mais avançadas de proteção de conteúdo.

