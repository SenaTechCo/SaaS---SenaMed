# SenaMed — Frontend

Aplicação web do SenaMed, um SaaS multi-tenant de agendamento online para clínicas e profissionais autônomos (dentistas, psicólogos, nutricionistas). Construída com Vite + React 19 + TypeScript.

## Stack

- React 19 + TypeScript
- React Router 7 (rotas autenticadas e públicas)
- Vite (dev server com proxy de `/api` para o backend em `localhost:8080`)
- Oxlint

## Pré-requisitos

- Node.js 18+
- Backend rodando em `http://localhost:8080` (veja o README do backend) com o Postgres do `docker-compose.yml` na raiz do repositório já de pé.

## Como rodar

```bash
npm install
npm run dev
```

A aplicação sobe em `http://localhost:5173`.

## Scripts

| Comando | Descrição |
| --- | --- |
| `npm run dev` | Sobe o servidor de desenvolvimento (Vite) |
| `npm run build` | Type-check (`tsc -b`) + build de produção |
| `npm run lint` | Lint com Oxlint |
| `npm run preview` | Serve o build de produção localmente |

## Estrutura de páginas

### Área autenticada (`/dashboard/**`, dentro de `ProtectedRoute`)

- `/dashboard` — início
- `/dashboard/medicos` — cadastro, edição e inativação de médicos (respeitando o limite de médicos do plano)
- `/dashboard/medicos/:id/horarios` — disponibilidade semanal do médico (múltiplas janelas por dia)
- `/dashboard/medicos/:id/folgas` — folgas pontuais do médico
- `/dashboard/personalizacao` — dados públicos e identidade visual da clínica (logo, capa, cores)

### Autenticação

- `/cadastro` — cadastro de uma nova clínica (cria a conta em trial)
- `/login` — login

### Área pública (sem autenticação)

- `/clinica/:slug` — página pública da clínica: escolha de médico, data e horário livre, e agendamento com consentimento LGPD obrigatório
- `/cancelar/:token` — cancelamento de agendamento via link enviado ao paciente

## Autenticação e chamadas à API

- `AuthContext` (`src/context/AuthContext.tsx`) mantém a sessão (token + dados da clínica/usuário) persistida em `localStorage`.
- `apiFetch` (`src/lib/http.ts`) centraliza as chamadas à API: injeta automaticamente o `Authorization: Bearer <token>` quando há uma sessão ativa e não faz nada quando não há (por isso as páginas públicas reaproveitam o mesmo helper).
- `ProtectedRoute` redireciona para `/login` quando não há sessão válida.
