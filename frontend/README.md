# SecureSOC Frontend

React + Vite single-page app for the SecureSOC / LAN Realtime Monitoring
System console. Built with TanStack Router (file-based routing, client-side
only — no SSR), TanStack Query, Tailwind CSS v4, and a small set of shadcn/ui
primitives.

## Setup

```bash
npm install
cp .env.example .env.local   # optional, see below
npm run dev
```

## Backend integration

By default the app runs against in-memory mock data (`VITE_USE_MOCKS=true`)
so the UI works with no backend running. To point it at the real Spring Boot
backend, set in `.env.local`:

```ini
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=http://localhost:8080/api/ws
VITE_USE_MOCKS=false
```

`src/api/authApi.ts` is already wired to `POST /auth/login`; the login page
uses it directly. Other pages (dashboard widgets, endpoint/alert lists, etc.)
currently render illustrative static data inline and are the next piece to
wire up to `src/api/` + TanStack Query, following the same pattern as auth.

## Structure

```
src/
  api/          Axios client + typed calls to the backend (auth wired; more to add)
  auth/         Token storage
  components/   AppShell (nav/layout) + a handful of shadcn/ui primitives
  hooks/        useIsMobile
  lib/          cn() className helper
  routes/       File-based routes (TanStack Router) — one file per page
  types/        Types mirroring backend DTOs
```

## Build

```bash
npm run build     # outputs to dist/
npm run preview   # serve the production build locally
```
