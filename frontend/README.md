# Ideia Space — Repositório Digital Protegido (frontend)

Frontend do desafio **Porto Digital · Squad 22**. React 19 + TypeScript + Vite 8 + Tailwind CSS 4.

> **Modo demonstração:** não há backend. Login, upload, publicação e logs são simulados em memória (faixa visível no app). Os arquivos de `public/demo/` são de exemplo e **não são protegidos**.

## Executar
Requer Node 20.19+ ou 22.12+.

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # tsc + build de produção
npm run preview
```

## Contas de demonstração (senha `Senha@123`)
| Perfil | E-mail |
|---|---|
| Professor | victor.emanoel@ideiaspace.dev |
| Aluno | maria.clara@ideiaspace.dev |
| Admin | admin@ideiaspace.dev |
| Aluno inativo (testa bloqueio) | joao.pedro@ideiaspace.dev |

Na tela de login, os botões "Contas de demonstração" preenchem o formulário.

## Estrutura
`src/components` (ui, layout, materials, viewer, security, brand) · `pages` · `layouts` · `hooks` · `services` (contratos + mock) · `mocks` · `types` · `utils` · `context`.

## Documentação
- `STATUS_DESENVOLVIMENTO.md` — estado atual e próximos passos
- `docs/REQUISITOS_E_INCONSISTENCIAS.md` — matriz de requisitos, inconsistências e decisões
- `docs/API_CONTRACT.md` — contrato proposto para o backend
- `.env.example` — variáveis de ambiente

## Proteção de conteúdo
Marca d'água nominal, bloqueio de atalhos, ocultação ao perder foco e registro de tentativas **dificultam** a reprodução e geram rastreabilidade. Não impedem fotos da tela; a proteção efetiva é do backend.
