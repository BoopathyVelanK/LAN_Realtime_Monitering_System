# Phase 4 Verification Report — SecureSOC

**Date:** 2026-07-20
**Role:** Verification only. No features implemented, no unrelated refactors, no new endpoints added. Every finding below was reached by reading the actual current source on `main` (backend Java, frontend TypeScript, Flyway SQL) and tracing execution paths through it — not by re-running prior audit conclusions from memory.

---

## 1. Executive Summary

Two **critical, previously-undetected bugs** were found by tracing execution flow rather than just reading code in isolation:

1. **`GET /endpoints` and `GET /laboratories` will throw `LazyInitializationException` (HTTP 500) for any row with its lazy relation populated** — i.e. any endpoint assigned to a lab, or any lab assigned to a department. This is currently dormant only because the agent's default `config.ini` ships with `lab_id` blank (unassigned), so it hasn't been hit yet in testing — but it is a landmine that detonates the instant normal admin usage (assigning a lab to an endpoint) happens.
2. **Unauthenticated requests to protected endpoints likely return HTTP 403, not 401**, because `SecurityConfig` never registers an `AuthenticationEntryPoint` and doesn't call `.httpBasic()`. The frontend's token-refresh interceptor is hard-coded to only react to `401`. Net effect: once an access token expires, the app silently stops working instead of refreshing or redirecting to login.

Everything else traced — the auth login/refresh/logout chain (excluding the above), the full monitoring ingest→read→UI pipeline for all 8 event types, DTO field consistency, database indexing, and mock-data discipline — is genuinely solid and correctly implemented. This is not a sloppy codebase; these two bugs are subtle, easy-to-miss interactions between a deliberate architectural choice (`open-in-view: false`) and two service methods that didn't apply the same discipline used everywhere else.

## 2. Authentication Verification

Traced: Login → JWT creation → frontend storage → Axios interceptor → Authorization header → protected API → refresh → logout → session restore.

| Step | Verified against | Result |
|---|---|---|
| Login (`POST /auth/login`) | `AuthController`, `AuthService.login()` | ✅ Correct — BCrypt check, account lockout after 5 failed attempts, JWT + refresh token issued |
| Frontend token storage | `auth/tokenStorage.ts`, `AuthContext.tsx` | ✅ localStorage, hydrated once on mount, invalid partial state cleared |
| Axios interceptor attaches token | `api/httpClient.ts` request interceptor | ✅ `Authorization: Bearer <token>` on every request when a token exists |
| Refresh on 401 | `httpClient.ts` response interceptor | ✅ **Correctly single-flight** — concurrent 401s dedupe into one `/auth/refresh` call via a shared `refreshPromise`, original requests retried after. No race condition found. |
| Refresh token invalid/expired/revoked | `AuthService.refresh()` → throws `UnauthorizedException` → `GlobalExceptionHandler` | ✅ Correctly returns 401 (this path never touches the security filter chain — `/auth/refresh` is `permitAll()`) |
| Logout | `AuthController`/`AuthService.logout()`, `authApi.ts` | ✅ Revokes refresh token server-side, clears local state |
| Session restore | `AuthContext.tsx` mount effect | ✅ Rehydrates from localStorage; mismatched/partial state is discarded, not trusted |
| **Missing/expired/malformed access token on a *protected* endpoint** | `SecurityConfig`, `JwtAuthenticationFilter` | ❌ **See Critical Issue #2 below** |
| 403 (authorization, not authentication) | — | Not specially handled anywhere (no role-gated routes exist yet) — acceptable for current scope |

### Critical Issue #2 — auth failures on protected routes likely return 403, not 401

**Trace:**
- `JwtAuthenticationFilter` (its own Javadoc): *"leaves the SecurityContext empty on a bad/missing/expired token deliberately — Spring Security's entry point turns that into a clean 401."*
- `SecurityConfig`: builds the filter chain with `authorizeHttpRequests(...).anyRequest().authenticated()`, adds `JwtAuthenticationFilter` and `AgentTokenAuthFilter` via `addFilterBefore(...)`, but **never calls `.httpBasic()`, `.formLogin()`, or `.exceptionHandling(e -> e.authenticationEntryPoint(...))`**.
- Neither `JwtAuthenticationFilter` nor `AgentTokenAuthFilter` registers an `AuthenticationEntryPoint` (they're plain `OncePerRequestFilter`s, not `SecurityConfigurer`s — only mechanisms like `.httpBasic()` register an entry point as a side effect).
- Spring Security's documented behavior: when `ExceptionTranslationFilter` needs to start authentication (anonymous user hit an `authenticated()` rule) and no entry point was explicitly configured or auto-registered by another mechanism, it falls back to **`Http403ForbiddenEntryPoint`**, which returns **403**, not 401.
- `frontend/src/api/httpClient.ts`: `if (error.response?.status === 401 && ...)` — strictly 401-gated, no 403 handling anywhere in the retry/refresh/logout chain.

**Impact:** once the access token expires (15 min by default), every subsequent request returns 403. The refresh flow never triggers, `registerAuthExpiredHandler`'s logout/redirect never fires. The user sees data silently fail to load app-wide with no path back to a working state short of manually logging out.

**Confidence:** high, based on reading the actual `SecurityConfig` and documented Spring Security 6 defaults — but **not empirically confirmed**, since I cannot run the Spring Boot server in this environment (no Maven Central access in this sandbox). This is a 30-second check the person can run directly: start the backend, then `curl -i http://localhost:8080/api/endpoints` with no `Authorization` header (or an expired one) and read the status line.

**Recommended fix** (not applied — out of scope for this verification pass): either add `.exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))` to `SecurityConfig`, or have `httpClient.ts`'s interceptor also treat 403 as a possible auth failure (less precise, since 403 could mean something else once role-based authorization exists later).

## 3. API / End-to-End Verification

### Critical Issue #1 — `LazyInitializationException` on `GET /endpoints` and `GET /laboratories`

**Root cause pattern:** `application.yml` sets `spring.jpa.open-in-view: false` (a deliberate, correct architectural choice — avoids the OSIV anti-pattern). This means lazy entity relations can **only** be accessed inside an active `@Transactional` boundary; there is no fallback "keep the session open for the whole request" safety net.

**Where this is done correctly** (compared for contrast): `MonitoringService`'s 7 list methods are each `@Transactional(readOnly = true)`, with the class Javadoc explicitly explaining why: *"so each mapped row's lazily-loaded entity.getEndpoint().getHostname() resolves within the same Hibernate session."* `AgentService.register()`/`heartbeat()` are both `@Transactional` and correctly access `device.getLab()` while still inside the transaction. This is the right pattern, applied correctly, twice.

**Where this is done incorrectly:**

- **`EndpointService.listAll()`** (pre-existing code): no `@Transactional` anywhere in the class. `endpointDeviceRepository.findAll()` runs and completes inside Spring Data JPA's own internal per-method transaction, which closes before `.stream().map(EndpointService::toSummary)` runs. Inside `toSummary`, `d.getLab().getId()` / `.getName()` is called — `EndpointDevice.lab` is `@ManyToOne(fetch = FetchType.LAZY)`. For any endpoint with a non-null `lab_id`, this throws `LazyInitializationException` → uncaught → `GlobalExceptionHandler`'s generic handler → HTTP 500. For endpoints with `lab_id IS NULL`, `getLab()` returns a real `null` directly (no proxy created), so **no exception** — which is exactly why this hasn't surfaced yet: the agent's `config.ini` ships with `lab_id` blank by default.
- **`LaboratoryService.listAll()`** (added in this project's previous session, alongside this verification): same bug, same root cause. `Laboratory.department` is also `@ManyToOne(fetch = FetchType.LAZY)`; `lab.getDepartment()` is called outside any transaction. Any laboratory with a `department_id` set will 500.
- **`DepartmentService.listAll()`**: **not affected** — it only calls `laboratoryRepository.countByDepartment_Id(...)`, a plain count query with no lazy entity access. Safe, just N+1-inefficient (see §6).

**Impact:** `GET /endpoints` is used by the Dashboard, `/endpoints`, `/live-systems`, `/endpoints/$id`, and `AppShell`'s global footer count — i.e. nearly every page in the app. `GET /laboratories` is used by `/laboratories` and `/departments` (endpoint-count rollup). Both will 500 for realistic data the moment labs/departments are actually assigned.

**Recommended fix** (not applied): add `@Transactional(readOnly = true)` to both `listAll()` methods, matching `MonitoringService`'s pattern exactly. (A `JOIN FETCH` in the repository query would be even better — see §6, same fix solves both the crash and the N+1.)

### Feature-by-feature trace (excluding the two issues above)

| Feature | Frontend hook | API call | Controller | Service | Repository | DTO fields match TS? | Status |
|---|---|---|---|---|---|---|---|
| Login/Refresh/Logout | `authApi.ts` | `/auth/*` | `AuthController` | `AuthService` | `UserRepository`, `RefreshTokenRepository` | ✅ | Working |
| Endpoint list | `useEndpoints` | `GET /endpoints` | `EndpointController` | `EndpointService` | `EndpointDeviceRepository` | ✅ fields match, ⚠ crash risk (see above) | Broken for assigned labs |
| Departments | `useDepartments` | `GET /departments` | `DepartmentController` | `DepartmentService` | `DepartmentRepository`, `LaboratoryRepository` | ✅ | Working (N+1, see §6) |
| Laboratories | `useLaboratories` | `GET /laboratories` | `LaboratoryController` | `LaboratoryService` | `LaboratoryRepository`, `EndpointDeviceRepository` | ✅ fields match, ⚠ crash risk (see above) | Broken for assigned depts |
| USB/VPN/Idle/Network/Internet/Running-apps (6 feeds) | `use*Events`/`useRunningAppSnapshots` | `GET /monitoring/*` | `MonitoringController` | `MonitoringService` | 6 event repositories | ✅ | Working, correctly transactional |

## 4. Monitoring Pipeline Verification

Traced Python agent → POST → entity → GET → frontend for all 8 monitoring types (login, logout, usb, vpn, idle, running-apps, network-usage, internet-usage):

- `agent.py`'s `_post_monitoring()` posts to `/monitoring/{path}` with `X-Agent-Token`; `AgentTokenAuthFilter` resolves the device; `MonitoringController` delegates to `MonitoringService.record*()`, each `@Transactional`, each persists via its repository.
- Every `MonitoringController` GET method clamps `size` to `[1, 200]` before calling the paginated `MonitoringService.list*()` methods — a sensible defensive bound not present in the request DTOs.
- Every response DTO's field order/names (verified for `UsbEventResponse`, `VpnEventResponse` as representative samples — record constructor argument order) matches `frontend/src/types/api.ts` exactly.
- USB action enum (`CONNECTED`/`DISCONNECTED`), VPN `active: boolean`, `InternetUsageEvent`'s `BigDecimal` upload/download fields (serialize to JSON numbers via Jackson, map cleanly to TS `number`) — all consistent.
- No broken chain found anywhere in this pipeline. This is the most solid part of the backend.

**Confirms:** all 6 monitoring pages genuinely display real backend data end-to-end, as previously reported.

## 5. DTO Validation

- Spot-checked `UsbEventResponse`/`VpnEventResponse`/`EndpointSummaryResponse`/`DepartmentResponse`/`LaboratoryResponse` Java records against their TypeScript mirrors in `types/api.ts` — field names, order, and nullability all consistent.
- UUIDs: Java `UUID` → JSON string → TS `string`, consistent everywhere.
- Enums: Java enums serialize via `.name()` calls in DTO construction (not raw enum objects) → plain JSON strings, matching TS string-literal unions.
- Dates: Java `Instant` → ISO-8601 JSON string (Jackson default) → TS `string`, consistent.
- No mismatches found in the fields checked.

## 6. Database Efficiency

**Indexing: genuinely thorough, no gaps found.** Checked `V1__init_schema.sql` and `V4__phase3_monitoring.sql`: every foreign key used in a query (`endpoint_id` on all 8 monitoring tables, `lab_id` on `endpoint_devices`, `department_id` on `laboratories`, `snapshot_id` on `running_apps`) has an explicit index, and every sort column used by a `findAllByOrderByXDesc`-style repository method also has one. This is not a common oversight to get right this consistently — worth calling out as a genuine strength.

**N+1 query patterns found** (none cause incorrect results, all are extra round-trips):

1. `MonitoringService`'s list methods: `e.getEndpoint().getHostname()` per row → 1 query for the page + up to `pageSize` (max 200, per the controller's clamp) extra single-row lookups, since no repository query uses `JOIN FETCH e.endpoint`.
2. `listRunningAppSnapshots`: additionally, `s.getApps()` (a lazy collection) accessed per snapshot row — another N+1 layer.
3. `DepartmentService.listAll()`: 1 query for departments + N `countByDepartment_Id` queries (one per department).
4. `LaboratoryService.listAll()`: 1 query for labs + 2N count queries (`countByLab_Id` + `countByLab_IdAndStatus` per lab), plus the crash risk noted in §3.

**Recommended fix** (not applied): convert the hot-path repository methods to `@Query(...JOIN FETCH...)` — this simultaneously fixes both the N+1 inefficiency and Critical Issue #1's crash, since a fetch-joined entity is fully initialized before the transaction (if any) closes.

## 7. Security Verification

- `SecurityConfig`: default-deny (`anyRequest().authenticated()`), with an explicit, narrow `permitAll()` allowlist (`/auth/**`, `/agents/**` — agent registration/heartbeat use their own token scheme, not JWT). `/departments`, `/laboratories`, `/endpoints`, `/monitoring/**` are **not** in the allowlist, so they correctly require a valid JWT.
- New `DepartmentController`/`LaboratoryController` endpoints added in the previous session required **no** `SecurityConfig` changes to be protected — confirms the default-deny posture is working as designed for anything new.
- No endpoint found to be accidentally public.
- The one real security-adjacent issue is Critical Issue #2 (§2) — not a hole (unauthenticated access is still correctly denied), but the *wrong status code* is returned, breaking the frontend's recovery flow.

## 8. Performance Verification

- `router.tsx` creates `new QueryClient()` with **no explicit defaults** — TanStack Query v5's built-in defaults apply: `retry: 3` (exponential backoff), `staleTime: 0`, `refetchOnWindowFocus: true`.
  - Combined with Critical Issue #2: a 403 auth failure gets retried 3× by React Query before surfacing as an error, on top of whatever `httpClient.ts`'s own interceptor does — wasted requests, slower failure feedback.
  - `staleTime: 0` means every component remount refetches, not just the first mount — e.g. `AppShell` (rendered fresh on every route navigation) calling `useEndpoints`/`useAlerts`/`useLiveFeed` means every page change refetches those, not just the initial load. Documented as a known caveat in `AppShell.tsx`'s own comments from the previous session; confirmed still accurate.
- No memory leaks or missing cleanup found in `useLiveFeed`'s STOMP subscription teardown.
- No unnecessary heavy client-side computation found (the `mockMetricsFor`/hash-based mock generators in `live-systems.tsx`/`departments.tsx`/`laboratories.tsx` are deterministic and cheap).

## 9. Remaining Mock Features

No change since `FRONTEND_INTEGRATION_AUDIT.md` (previous session) except Departments/Laboratories moving from full-mock to mixed real/mock — already reflected there. Every remaining mock is **SAFE MOCK** (backend genuinely doesn't exist: Alerts, Risk, Detection Rules, IOC, Reports, Exam Mode, Audit Logs, Backup, Inventory, Users, Faculty, Student Activity, WebSocket push). No **BUG**-class mock (backend exists but frontend ignores it) was found in this pass.

## 10. Bugs Found

1. **[CRITICAL]** `GET /endpoints` 500s for any endpoint with a lab assigned (§3).
2. **[CRITICAL]** `GET /laboratories` 500s for any lab with a department assigned (§3).
3. **[CRITICAL]** Protected-endpoint auth failures likely return 403 instead of 401, silently breaking the frontend's refresh/re-login flow (§2).
4. **[MODERATE]** N+1 query patterns in `MonitoringService`, `DepartmentService`, `LaboratoryService` (§6).
5. **[MINOR]** No explicit React Query defaults set — amplifies the impact of #3, minor extra network chatter (§8).

## 11. Critical Issues

Issues 1–3 above. All three are real, code-traced, and currently either dormant (1, 2 — no lab/department assignments exercised yet) or unverified-at-runtime (3 — needs a live curl check). None were flagged in the prior `FRONTEND_INTEGRATION_AUDIT.md`, which correctly scoped the *frontend* wiring but didn't trace backend transaction boundaries.

## 12. Recommended Fixes

*Not applied in this pass — verification only, per instructions.*

1. Add `@Transactional(readOnly = true)` to `EndpointService.listAll()` and `LaboratoryService.listAll()` (smallest possible fix for the crash) — or better, add `JOIN FETCH` to the underlying repository queries (fixes the crash and the N+1 in one change).
2. Add an explicit `AuthenticationEntryPoint` to `SecurityConfig` that writes a 401 status, so filter-chain auth failures match what `httpClient.ts` already expects.
3. Once #1 is fixed, revisit `MonitoringService`'s hostname/apps N+1 pattern the same way, for consistency and to head off performance issues at scale.
4. Consider setting sane `QueryClient` defaults (e.g. `retry: 1`, explicit `staleTime`) once #2 is fixed, so a genuine auth failure doesn't get masked by 3 retries.

## 13. Production Readiness Checklist

| Item | Status |
|---|---|
| No hardcoded fake status/stats in the UI | ✅ (fixed previous session — `AppShell.tsx`) |
| No fake alerts/notifications shown as if real | ✅ (all mock alert UI is labeled) |
| No fake endpoint counts | ✅ |
| No fake uptime | ✅ (removed previous session) |
| WebSocket honestly shows disconnected when backend is absent | ✅ |
| Every implemented monitoring page shows real data | ✅ |
| `GET /endpoints` works for realistic (lab-assigned) data | ❌ **will 500** |
| `GET /laboratories` works for realistic (dept-assigned) data | ❌ **will 500** |
| Token expiry triggers refresh or logout, not silent failure | ❌ **likely broken (403 vs 401)** |
| All protected endpoints require JWT | ✅ |
| DB indexing adequate for current query patterns | ✅ |

## 14. Deployment Checklist

- [ ] Fix Critical Issues #1–3 before any deployment involving real lab/department assignment or session durations longer than one access-token lifetime (15 min default).
- [ ] Empirically confirm the 401-vs-403 behavior with a live `curl` request (cannot be done from this environment).
- [ ] Run `mvn clean package` and `npm run build` locally (still outside this environment's capabilities) — no build-breaking issues were found by static tracing, but this hasn't been executed.
- [ ] Everything else (auth chain minus #3, all 6 monitoring feeds, DTO consistency, indexing, mock-data discipline) is ready as-is.

---

## FINAL VERDICT

**❌ NOT VERIFIED**

Two critical, currently-dormant crash bugs (`GET /endpoints`, `GET /laboratories`) and one high-confidence authentication-flow bug (403-vs-401) were found. All three are narrow, well-understood, and cheap to fix, but they are real, will affect normal usage, and were not caught by the "backend builds successfully" / "frontend builds successfully" status checks reported earlier — those checks prove compilation, not these runtime execution paths. Per the instruction to only return VERIFIED if every implemented feature traces cleanly end-to-end without any broken integration: it does not, yet.
