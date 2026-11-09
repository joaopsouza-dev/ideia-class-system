# Contratos de integração com o backend (PROPOSTA)

> Nenhum endpoint abaixo existe ainda. Este documento descreve **o que o frontend precisa**, para a equipe de backend validar ou ajustar. As interfaces TypeScript correspondentes estão em `src/services/contracts.ts` e os tipos em `src/types/index.ts`.

## Como trocar o mock pela API
1. Crie adapters HTTP que implementem `Services` (`src/services/contracts.ts`).
2. Em `src/services/index.ts`, substitua `httpServices`.
3. Defina `VITE_USE_MOCKS=false` e `VITE_API_BASE_URL` (ver `.env.example`).

## Requisitos transversais (backend)
- Autorização real por perfil/turma (RLS). O frontend apenas esconde o que a API não devolve.
- Respostas **404** (não 403) para conteúdo de turma alheia, como descrito na Sprint 2.
- Conta inativa → **401** com código distinguível (`ACCOUNT_DISABLED`).
- Nunca devolver `caminho_storage`, `hash_senha` ou CPF.
- URLs de PDF/vídeo **assinadas, curtas e por usuário**; o viewer tenta de novo se expirar.
- `ip_origem` e `dispositivo` devem vir do servidor (o frontend não é confiável).

## Serviços e operações

| Serviço | Operação | Proposta HTTP | Notas |
|---|---|---|---|
| auth | login | `POST /api/auth/login` | Retorna sessão/usuário; cookie httpOnly ou JWT |
| auth | register | `POST /api/auth/register` | Perfil definido pelo servidor |
| auth | requestPasswordReset | `POST /api/auth/password-reset` | Resposta neutra |
| auth | logout / restore | `POST /api/auth/logout`, `GET /api/auth/me` | |
| academic | listTurmas / getTurma | `GET /api/turmas`, `GET /api/turmas/{id}` | Filtradas pelo vínculo (`turma_usuario`) |
| academic | listTurmasGerenciaveis | `GET /api/turmas?papel=DOCENTE` | Para o seletor do upload |
| academic | listUsuarios / setUsuarioAtivo | `GET /api/usuarios`, `PATCH /api/usuarios/{uuid}` | Somente ADMIN |
| materials | list / get | `GET /api/conteudos`, `GET /api/conteudos/{uuid}` | Sem itens com `removido_em` |
| materials | create / update | `POST /api/conteudos`, `PATCH /api/conteudos/{uuid}` | Upload: multipart ou URL de upload assinada + progresso |
| materials | setStatus | `PATCH /api/conteudos/{uuid}` | Requer coluna de publicação (ver inconsistência 3) |
| materials | remove | `DELETE /api/conteudos/{uuid}` | Soft delete |
| content | getViewerSource | `GET /api/conteudos/{uuid}/acesso` | `{ url, expiraEm }`; deve registrar `ABRIU_*` |
| accessLog | record | `POST /api/acessos` | Tentativas de captura/impressão/etc. |
| accessLog | list | `GET /api/acessos` | ADMIN: todos; PROFESSOR: seus conteúdos |

## Pontos de decisão para a equipe
- **Streaming:** se o backend entregar HLS/DASH (`.m3u8`), adicionar `hls.js` ao `VideoPlayer`. Com MP4 assinado já funciona.
- **Histórico "continuar de onde parou":** hoje em `localStorage` (por dispositivo). Se desejado por usuário: `PUT /api/conteudos/{uuid}/progresso`.
- **Limites de upload:** `VITE_MAX_PDF_MB` / `VITE_MAX_VIDEO_MB` são provisórios; definir os reais.
- **Tipos de ação do log:** alinhar a enumeração `TipoAcao`.
