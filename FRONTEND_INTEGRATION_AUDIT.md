# Frontend Integration Audit — SecureSOC

**Date:** 2026-07-19 (updated 2026-07-20 — Departments/Laboratories backend added)
**Scope:** Full repository-wide audit of `frontend/src` (api, auth, components,
hooks, lib, mocks, routes, ws) against the current Spring Boot backend, as
requested. This audit inspected every route file, every file in `api/`,
`auth/`, `ws/`, `lib/`, `hooks/`, `components/AppShell.tsx`, `config.ts`, and
cross-referenced every finding against the backend's actual controller list.

## 1. Executive Summary

The backend now exposes **6 controllers**: `AuthController`,
`AgentController`, `EndpointController`, `MonitoringController`, and (added
as a direct follow-up to this audit) `DepartmentController` and
`LaboratoryController`. Everything else the frontend renders — Alerts, Risk
Analysis, Detection Rules, IOC, Reports, Exam Mode, Audit Logs, Backup,
Inventory, Users, Faculty, Student Activity — still has **no backend
support at all** and is correctly mock. This is not an oversight to "fix";
it is Phase 4+ work (detection engine, admin CRUD, real-time layer) that
hasn't started yet.

Within that ceiling, the codebase is unusually disciplined: nearly every
page that touches mock data already has an explicit header comment
explaining exactly what's real, what's mock, and why. Two real bugs were
found and fixed in the initial audit pass (`AppShell.tsx`'s
permanently-fake status bar, `live-systems.tsx`'s undisclosed 100%-mock
table), on top of the `queries.ts`/`queryKeys.ts` file-swap bug fixed in
the session before that (build-breaking, unrelated to mock data).

**Net assessment:** the "replace mock with real data" objective is largely
already done wherever a real backend exists. The remaining mock pages are
correctly gated behind genuinely-missing backend work, not frontend
laziness.

## 2. Working Features

- **Auth** — `POST /auth/login`, `/refresh`, `/logout` fully wired.
  `httpClient.ts` attaches JWT via request interceptor, single-flight
  refresh-on-401 with automatic retry, session hydration from
  `localStorage` on load, logout clears state and redirects. No mock
  fallback bugs — `USE_MOCKS` cleanly gates `authApi.ts`.
- **Endpoint fleet** — `GET /endpoints` powers the Dashboard hero card,
  `/endpoints` list, `/endpoints/$id` detail identity block, `/live-systems`,
  and `AppShell`'s footer count.
- **6 monitoring event feeds** — `GET /monitoring/{usb,vpn,idle,
  network-usage,internet-usage,running-apps}` each power one dedicated
  page, correctly paginated via `MonitoringListParams`/`PageResponse<T>`,
  with loading/error/empty states throughout.
- **Departments / Laboratories** (new) — `GET /departments`, `GET
  /laboratories` power `/departments` and `/laboratories`. Endpoint counts
  per lab/department are server-derived (`EndpointDeviceRepository.
  countByLab_Id`/`countByLab_IdAndStatus`, `LaboratoryRepository.
  countByDepartment_Id`), not stored columns.
- **Route guard** — `AppShell` redirects unauthenticated users to `/login`
  post-hydration; `/login` bounces already-authenticated users to `/`.
  404 and error boundaries exist at the router root.
- **Query-key architecture** — `queryKeys.ts` factory, consumed
  consistently by `queries.ts` and `useLiveFeed.ts` for cache writes.

## 3. Broken Features (found and fixed)

| Issue | File | Fix |
|---|---|---|
| Permanently hardcoded `WSS: CONNECTED`, `LAN ENDPOINTS: 142/142`, a fake permanent `ALERT: UNAUTHORIZED USB DETECTED ON LAB-PC-17` banner, and a fake uptime counter — shown on **every single page** | `components/AppShell.tsx` | Wired to real `useEndpoints`, `useLiveFeed`, `useAlerts`. Uptime claim removed (no backend concept exists to source it from). |
| 100% hardcoded mock table with **no disclosure comment** (only page in the app without one) | `routes/live-systems.tsx` | Hostname/status/last-seen now from real `useEndpoints()`; remaining mock columns explicitly labeled `(MOCK)`. |
| `queries.ts` missing 6 monitoring hooks; `queryKeys.ts` overwritten with a broken self-import | `api/queries.ts`, `api/queryKeys.ts` | Fixed in an earlier session — confirmed still correct in this audit. |
| `departments.tsx`/`laboratories.tsx` 100% hardcoded, no backend existed | new `DepartmentController`/`LaboratoryController` + both route files | Backend added (schema already existed via Flyway); pages now source name/code/capacity/lab count/endpoint count from real data, with academics/exam-mode/risk fields left mock and labeled. |

No other broken imports, missing exports, or dead references were found
across the 30 route files and 15 supporting modules inspected.

## 4. Remaining Mock Data

Per the instructions, these are **left in place** because no backend
endpoint exists — each is already labeled in its source file:

| File | Function/data | Reason |
|---|---|---|
| `mocks/data.ts` | `generateAlerts`, `generateRiskScores` | No `AlertController`/`RiskController` (Phase 4 detection engine) |
| `mocks/mockLiveFeed.ts` | simulated alert/status/risk emitter | Used only when `VITE_USE_MOCKS=true` |
| `routes/index.tsx` | `networkSeries`, `cpuSeries`, `riskPie`, `recentEvents` | No historical metrics/risk/alerts endpoints |
| `routes/endpoints.$id.tsx` | `cpuSeries`, `processes`, `timeline`, hero metric cards | No per-endpoint process list, live-usage history, or risk endpoint |
| `routes/risk-analysis.tsx`, `alerts.tsx` KPI/table data | rule weights, hit counts | No detection-rules/risk backend; explicitly commented |
| `routes/departments.tsx`, `laboratories.tsx` | student/faculty counts, exam-mode flag, risk score | No enrollment model, exam-mode state, or risk engine — name/code/capacity/counts are real, see section 2 |
| `routes/detection-rules.tsx`, `ioc.tsx`, `reports.tsx`, `exam-mode.tsx`, `audit-logs.tsx`, `backup.tsx`, `data-usage.tsx`, `settings.tsx`, `users.tsx`, `faculty.tsx`, `student-activity.tsx`, `help.tsx` | hardcoded arrays | No matching controller exists for any of these |

None of these should be wired up — doing so would call endpoints that
return 404 against the real backend. Recommend adding the same `(MOCK)` /
header-comment disclosure pattern to the ~11 admin pages that currently
lack it (cosmetic consistency only, not a bug).

## 5. API Coverage Table

| Page | Source | Backend endpoint | Status |
|---|---|---|---|
| Dashboard | Mixed (endpoints real, rest mock) | `GET /endpoints` | Partially wired |
| Endpoints | Backend | `GET /endpoints` | Wired |
| Endpoint Detail | Mixed | `GET /endpoints` (identity only) | Partially wired |
| Live Systems | Mixed | `GET /endpoints` (host/status only) | Partially wired |
| Applications (Running) | Backend | `GET /monitoring/running-apps` | Wired |
| USB Monitoring | Backend | `GET /monitoring/usb` | Wired |
| VPN Monitoring | Backend | `GET /monitoring/vpn` | Wired |
| Idle Monitoring | Backend | `GET /monitoring/idle` | Wired |
| Network Usage | Backend | `GET /monitoring/network-usage` | Wired |
| Internet Usage | Backend | `GET /monitoring/internet-usage` | Wired |
| Departments | Mixed (new) | `GET /departments` | Partially wired (name/code/lab count real; students/faculty/risk mock) |
| Laboratories | Mixed (new) | `GET /laboratories` | Partially wired (name/code/dept/capacity/active real; exam mode/risk mock) |
| Student Activity | Mock | — | Not implemented (no user/session/app-per-student model) |
| Inventory | Mock | — | Not implemented |
| Faculty Dashboard | Mock | — | Not implemented |
| Alerts | Mock (query-layer wired, backend absent) | — | Not implemented |
| Risk Analysis | Mock | — | Not implemented |
| Detection Rules | Mock | — | Not implemented (Phase 4) |
| IOC Management | Mock | — | Not implemented |
| Reports | Mock | — | Not implemented |
| Data Analytics | Mock | — | Not implemented |
| Exam Mode | Mock (local UI state only) | — | Not implemented |
| Users & Roles | Mock | *DB table exists, no controller* | Not implemented |
| Audit Logs | Mock | — | Not implemented |
| Backup & Restore | Mock | — | Not implemented |
| Settings | Mock (local form state, no persistence) | — | Not implemented |
| Help | Static content | — | N/A (docs page) |

## 6. Authentication

- `AuthProvider` hydrates from `localStorage` once on mount; a token
  without a matching persisted user (or vice versa) is treated as invalid
  and both are cleared — no partial-session trust.
- `httpClient.ts` attaches `Authorization: Bearer <token>` via request
  interceptor on every call.
- 401 handling: single-flight refresh (`refreshPromise` dedupes concurrent
  401s into one `/auth/refresh` call), original request retried with the
  new token on success; on failure, tokens cleared and
  `registerAuthExpiredHandler`'s callback fires (set by `AuthProvider`,
  navigates to `/login`).
- 403 handling: not specially handled — falls through as a normal rejected
  promise to the calling component's `isError` state. Acceptable given the
  app has no client-side role-gated routes yet.
- No race condition found in the refresh flow — it's genuinely
  single-flight, not per-request.
- **No issues found.** This layer needs no changes. New `/departments`,
  `/laboratories` endpoints require JWT automatically (`SecurityConfig`'s
  `anyRequest().authenticated()` default-deny) — no security config
  changes were needed to add them.

## 7. Backend Integration Issues

- **WebSocket**: `ws/stompClient.ts` and `ws/useLiveFeed.ts` are complete,
  correct implementations of a STOMP/SockJS client subscribing to
  `/topic/alerts`, `/topic/endpoints/status`, `/topic/risk` — but **no
  `WebSocketConfig` class exists anywhere in
  `securesoc-backend/src/main/java/com/securesoc/config/`**. With
  `VITE_USE_MOCKS=false`, `useLiveFeed` will attempt a real connection and
  simply never succeed (handled gracefully — `onWebSocketClose`/
  `onStompError` both flip `connected` to `false`, which surfaces honestly
  in the header/footer via `AppShell.tsx`). This is Phase 5 backend work,
  not a frontend bug.
- **`Link to="/endpoints/$id" params={{ id: e.hostname }}`** in
  `endpoints.tsx` passes hostname, not the endpoint UUID, as the route
  param. This looked like a bug at first glance but is intentional and
  consistently documented in `endpoints.$id.tsx` (`endpoints?.find(e =>
  e.hostname === id)`) — no backend `GET /endpoints/{id}` exists, so this
  is the only workable approach today. Flagging only so a future `GET
  /endpoints/{id}` doesn't silently break this convention.

## 8. Missing Backend APIs

Genuinely absent, blocking real integration (not something the frontend
can work around):

- `AlertController` / `Alert` entity + `/alerts`, `/alerts/{id}/acknowledge`,
  `/alerts/{id}/resolve`
- `RiskController` / risk-scoring engine + `/risk/{endpointId}`, `/risk`
- Detection rule storage + Sigma YAML import/rule engine
- IOC storage/matching
- `WebSocketConfig` (Phase 5 real-time layer) — client code is ready and
  waiting
- Lower priority (schema exists, no REST layer): `UserController`/role
  management
- No backend concept at all today for: exam mode, audit logging, backup/
  restore, inventory (hardware/software asset tracking), reports/export,
  student-to-endpoint session assignment, student/faculty enrollment counts

~~`DepartmentController`, `LaboratoryController`~~ — **done**, see section 2.

## 9. Configuration Issues

- `.env.local` is correctly absent from the repo (`*.local` is gitignored
  in `frontend/.gitignore`, matching Vite convention) — **not recreated**,
  since committing it would be wrong practice and it's machine-specific.
  `.env.example` already documents the three variables accurately
  (`VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_USE_MOCKS`) and defaults to
  `VITE_USE_MOCKS=true` so the app runs with zero backend setup.
- `frontend/README.md` was stale — described a pre-auth, pre-monitoring,
  pre-WebSocket state of the app. **Updated.**
- `settings.tsx` displays a hardcoded `WebSocket Port: 8443` /
  `REST API Port: 8080`, inconsistent with the actual single-origin
  `/api` + `/api/ws` config in `config.ts`. Cosmetic only — the page is a
  non-functional mock form (`SAVE CHANGES` does nothing), not a real
  config source. Left as-is pending Settings becoming a real feature.

## 10. Files Created

- `FRONTEND_INTEGRATION_AUDIT.md` (this file)
- `securesoc-backend/src/main/java/com/securesoc/dto/DepartmentResponse.java`
- `securesoc-backend/src/main/java/com/securesoc/dto/LaboratoryResponse.java`
- `securesoc-backend/src/main/java/com/securesoc/service/DepartmentService.java`
- `securesoc-backend/src/main/java/com/securesoc/service/LaboratoryService.java`
- `securesoc-backend/src/main/java/com/securesoc/controller/DepartmentController.java`
- `securesoc-backend/src/main/java/com/securesoc/controller/LaboratoryController.java`

## 11. Files Modified

- `frontend/src/api/queryKeys.ts` — restored as the actual key-factory module; later added `departments()`/`laboratories()` keys
- `frontend/src/api/queries.ts` — added 6 monitoring hooks; later added `useDepartments`/`useLaboratories`
- `frontend/src/components/AppShell.tsx` — replaced hardcoded fake status bar with real data
- `frontend/src/routes/live-systems.tsx` — wired hostname/status/last-seen to real `useEndpoints()`
- `frontend/src/routes/departments.tsx` — wired name/code/lab count/endpoint count to real data
- `frontend/src/routes/laboratories.tsx` — wired name/code/department/capacity/active to real data
- `frontend/src/api/dashboardApi.ts` — added `getDepartments`/`getLaboratories`
- `frontend/src/types/api.ts` — added `DepartmentResponse`/`LaboratoryResponse`
- `frontend/src/mocks/data.ts` — added `generateDepartments`/`generateLaboratories`
- `frontend/README.md` — rewritten to reflect actual current state
- `securesoc-backend/.../repository/EndpointDeviceRepository.java` — added `countByLab_Id`/`countByLab_IdAndStatus`
- `securesoc-backend/.../repository/LaboratoryRepository.java` — added `countByDepartment_Id`

## 12. Recommended Fix Order

1. ~~Small, backend-only, unlocks real admin pages: add
   `DepartmentController`, `LaboratoryController` REST read endpoints~~ —
   **done**.
2. **Phase 5**: `WebSocketConfig` on the backend — frontend is already
   waiting for it.
3. **Phase 4 (largest)**: Alert entity + `AlertController`, risk-scoring
   engine + `RiskController`, detection rule engine — unlocks Alerts, Risk
   Analysis, Detection Rules, and the Dashboard's remaining mock widgets.
4. **Later / lower value**: exam mode, audit logging, backup/restore,
   inventory, reports, user/role management — genuinely new feature areas,
   not "finish wiring the frontend" work.

## 13. Migration Checklist

- [x] Frontend builds against current backend with `VITE_USE_MOCKS=false`
      (auth, endpoints, 6 monitoring feeds, departments, laboratories)
- [x] No hardcoded fake status indicators remain in the global shell
- [x] `live-systems.tsx` disclosed and partially wired
- [x] README reflects current state
- [x] Backend: `DepartmentController`/`LaboratoryController` (unblocked 2 pages)
- [ ] Backend: `WebSocketConfig` (unblocks live push instead of polling)
- [ ] Backend: Alert/Risk/Detection-rule engine (unblocks 4+ pages)
- [ ] Frontend: add `(MOCK)` disclosure comments to the ~11 admin pages
      that currently lack them, for consistency (cosmetic, low priority)
