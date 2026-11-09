# Matriz de requisitos × frontend — Porto Digital Squad 22

Legenda de status: **Real** = funciona no frontend · **Simulado** = funciona com dados/mock em memória (marcado na UI) · **Backend** = depende do servidor para ser efetivo.
Origem: **D** = Desafio original · **S** = Resumo Sprint 2 / Modelo Lógico · **P** = pedido da equipe (prompt de desenvolvimento) · **T** = sugestão técnica (não obrigatória).

| # | Funcionalidade | Origem | Perfil | Tela / arquivo | Status | Dependência de backend | Critério de aceitação |
|---|---|---|---|---|---|---|---|
| 1 | Login com validação e feedback | D/S | Todos | `pages/auth/LoginPage` | Simulado (auth demo) | `POST` autenticação, JWT, 401 p/ conta inativa | Erros de campo, credencial inválida e conta inativa são distintos |
| 2 | Cadastro | P | Visitante | `pages/auth/RegisterPage` | Simulado | Criação de usuário; CPF criptografado (pgcrypto) | CPF validado por dígitos; perfil nunca escolhido pelo usuário |
| 3 | Recuperação de senha | P | Visitante | `ForgotPasswordPage` | Simulado | Envio de e-mail/token | Resposta neutra (não revela se a conta existe) |
| 4 | Encerrar sessão | P | Todos | `AppShell` | Real (limpa sessão demo) | Revogação de token | Volta ao login; rotas protegidas redirecionam |
| 5 | Controle por perfil | D | Todos | `layouts/RouteGuards`, `nav.ts` | Real (UX) + **Backend** | Autorização real por RLS | Aluno em rota de professor/admin → 403 |
| 6 | Acesso por turma/disciplina | D | Aluno/Prof. | `services/mock` (`visivel`) | Simulado + **Backend** | RLS (`usuario_is_member_of_turma`) | Aluno só vê conteúdos publicados das suas turmas |
| 7 | Upload e organização por disciplina/turma | D | Prof. | `MaterialEditorPage`, `FileDropzone` | Simulado (arquivo fica no navegador) | Upload ao storage (URL assinada) | Validação de tipo/tamanho, progresso, cancelar, erro |
| 8 | Edição, publicar/despublicar, excluir c/ confirmação | P | Prof. | `ManageList`, `ManageRow`, editor | Simulado | PATCH/DELETE (soft delete `removido_em`) | Confirmação antes de excluir; feedback por toast |
| 9 | Visualizador de PDF próprio, sem download | D | Todos | `viewer/PdfViewer` | Real (pdf.js) | URL temporária assinada | Canvas; sem `<a download>`; zoom, páginas, tela cheia |
| 10 | Streaming de vídeo sem download | D | Todos | `viewer/VideoPlayer` | Real (`<video>` próprio) | URL de streaming (possível HLS) | Sem controles nativos/`nodownload`; seek, volume, velocidade, tela cheia |
| 11 | Marca d'água dinâmica nominal | D | Todos | `security/Watermark` | Real (dados da sessão) | Sessão real do usuário | Nome, e-mail, sessão e hora; selo que muda de posição; sobrevive à tela cheia |
| 12 | Bloqueio de atalhos / detecção de tentativas | D | Todos | `hooks/useContentProtection` | Real (dissuasivo) | Persistência do log | PrintScreen, Ctrl+P/S/C, menu de contexto, perda de foco → aviso + registro |
| 13 | Restauração da marca d'água adulterada | D (rastreab.) | Todos | `ProtectedSurface` | Real (dissuasivo) | — | Remoção via DOM é revertida e registrada |
| 14 | Registro de acessos (log) | D/S | Prof./Admin | `AuditPage`, `accessLog` | Simulado | `log_acesso` imutável, IP/dispositivo do servidor | Lista, filtros, exportação CSV |
| 15 | Biblioteca: busca, filtros, ordenação, paginação | P | Todos | `LibraryPage`, `useLibrary` | Real (client-side) | Idealmente filtros no servidor | Estado vazio e "limpar filtros" |
| 16 | Painéis por perfil | P | Todos | `TeacherDashboard`, `StudentDashboard`, `AdminDashboard` | Simulado | Agregações | Métricas coerentes com os dados |
| 17 | Identificação de conteúdo novo / protegido | P | Aluno | `MaterialCard` | Real | — | "Novo" ≤ 7 dias; selo "Protegido" |
| 18 | Continuar de onde parou | P (condicional) | Aluno | `useProgress` | Real (localStorage, por dispositivo) | Histórico por usuário | Retoma página/tempo |
| 19 | Administração: usuários e auditoria | S (perfil ADMIN) | Admin | `UsersPage`, `AuditPage` | Simulado | CRUD de usuários | Ativar/desativar com confirmação |

## Inconsistências encontradas e interpretação adotada

1. **Perfil ADMIN.** O Desafio cita apenas professor/aluno; o banco (Sprint 2) define `ADMIN`. → Implementada área administrativa enxuta (usuários, turmas, auditoria). Se a equipe preferir só 2 perfis, remover rotas `/usuarios` e o perfil em `nav.ts`.
2. **Cadastro.** Nenhum documento exige autocadastro; o seed sugere usuários criados internamente. → Tela mantida (existia no código) e marcada como demonstração. Decisão pendente: manter, ou cadastro só por admin.
3. **Publicar/despublicar.** O modelo lógico não tem coluna de status de publicação (só `removido_em`). → UI usa `status` (`PUBLICADO`/`RASCUNHO`). **Backend precisa de uma coluna** (ex.: `publicado boolean` ou `publicado_em`).
4. **Conteúdo ↔ turma.** `Conteudo` tem um único `turma_id`; um material não pode ser compartilhado por várias turmas. → UI segue o modelo (uma turma por material).
5. **Exibição de ID.** Banco usa `id integer` e `uuid_publico`. → Frontend usa apenas `uuid_publico` (string) para conteúdos/usuários.
6. **`caminho_storage`.** Nunca deve chegar ao navegador; o resumo cita Signed URLs do Firebase. → Contrato `getViewerSource` devolve só uma URL temporária (+ expiração).
7. **Marca "Azul Vácuo".** O brandbook lista #E80074 (igual à Magenta) para "Azul Vácuo" — parece erro de digitação. → Usado o Oxford Blue **#0C0931** (página de cores do próprio brandbook) para o fundo escuro.
8. **Logotipo oficial.** O arquivo vetorial da assinatura não estava disponível em texto. → `components/brand/Logo.tsx` usa marca provisória; trocar pelo SVG oficial.
9. **Site institucional.** A leitura automática trouxe só texto (tom: "Abrimos o acesso ao espaço", "Da Ideia ao Espaço"), sem CSS. → A identidade segue o PDF; o tom de voz do site foi usado nos textos.
10. **Tipos de ação do log.** Só `ABRIU_PDF` aparece nos docs. → Enumeração `TipoAcao` em `types/index.ts` é **proposta** e precisa ser alinhada com o backend.

## Decisões técnicas registradas (para a base de conhecimento)

- **D-01** React Router (`react-router-dom`) substitui o fluxo por `useState`: URLs reais, botão voltar e links diretos.
- **D-02** `pdfjs-dist` para o PDF: desenha em `<canvas>`, sem camada de texto e sem barra nativa de download.
- **D-03** `lucide-react` para ícones; `@fontsource` (Montserrat e Poppins) para fontes locais no lugar das URLs do Figma.
- **D-04** Camada `services/` com interfaces (`contracts.ts`) e adapter mock; troca por HTTP em um único arquivo (`services/index.ts`).
- **D-05** Dependência `express` removida: o projeto é só frontend e ela não era usada.
- **D-06** Proteção no navegador é **dissuasiva e geradora de evidência**, não segurança absoluta (fotos da tela não são impedíveis).
