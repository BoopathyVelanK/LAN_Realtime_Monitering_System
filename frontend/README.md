# SecureSOC Frontend

React + Vite single-page app for the SecureSOC / LAN Realtime Monitoring
System console. Built with TanStack Router (file-based routing, client-side
only — no SSR), TanStack Query, Tailwind CSS v4, Axios, STOMP/SockJS, and a
small set of shadcn/ui primitives.

## Setup

```bash
npm install
cp .env.example .env.local   # required to point at a real backend - see below
npm run dev
```

## Backend integration

Toggled by `VITE_USE_MOCKS` in `.env.local` (see `.env.example` /
`src/config.ts`):

```ini
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=http://localhost:8080/api/ws
VITE_USE_MOCKS=false
```

- `VITE_USE_MOCKS=true` (default if `.env.local` is absent): runs entirely
  against in-memory mock data (`src/mocks/data.ts`) and a simulated live
  feed (`src/mocks/mockLiveFeed.ts`) — no backend required.
- `VITE_USE_MOCKS=false`: talks to the real Spring Boot backend.

**Current real-backend coverage** (see `src/api/dashboardApi.ts`'s header
comment for the authoritative, always-up-to-date list):
- Auth: `POST /auth/login`, `/refresh`, `/logout` — fully wired
  (`src/api/authApi.ts`, `src/api/httpClient.ts`'s single-flight refresh
  interceptor, `src/auth/AuthContext.tsx`).
- `GET /endpoints` — fully wired (dashboard, endpoints list/detail, live
  systems).
- `GET /monitoring/{usb,vpn,idle,network-usage,internet-usage,running-apps}`
  — fully wired (one page each, see `src/routes/`).
- Alerts, Risk Analysis, Detection Rules, IOC, Reports, Exam Mode, Audit
  Logs, Backup, Inventory, Departments, Laboratories, Users, Faculty,
  Student Activity: **no backend controller exists yet** for any of these
  (only 4 controllers exist server-side: Auth, Agent, Endpoint,
  Monitoring). These pages intentionally stay on mock/illustrative data,
  clearly marked `(MOCK)` in the UI and commented in the corresponding
  route file — do not wire them to a real endpoint that doesn't exist.
- WebSocket (`src/ws/stompClient.ts`, subscribing to `/topic/alerts`,
  `/topic/endpoints/status`, `/topic/risk`): frontend code is real and
  complete, but **no `WebSocketConfig` exists on the backend yet** — with
  mocks off, `useLiveFeed` will attempt to connect and simply stay
  disconnected (shown honestly in the header/footer status) until that
  backend work lands.

See `FRONTEND_INTEGRATION_AUDIT.md` at the repo root for the full audit.

## Structure

```
src/
  api/          Axios client (httpClient.ts), typed backend calls
                (authApi.ts, dashboardApi.ts), TanStack Query hooks
                (queries.ts) + query-key factory (queryKeys.ts)
  auth/         AuthContext (session state, login/logout) + tokenStorage
  components/   AppShell (nav/layout/status bar) + shadcn/ui primitives
  hooks/        use-mobile (viewport helper)
  lib/          cn() className helper, endpointFormat (status/time formatting)
  mocks/        In-memory mock data + simulated live feed (VITE_USE_MOCKS=true only)
  routes/       File-based routes (TanStack Router) — one file per page
  types/        Types mirroring backend DTOs field-for-field where a real
                DTO exists; contract-first types otherwise (see types/api.ts header)
  ws/           STOMP/SockJS client + useLiveFeed hook (real-time alerts/status/risk)
```

## Build

```bash
npm run build     # outputs to dist/
npm run preview   # serve the production build locally
```
