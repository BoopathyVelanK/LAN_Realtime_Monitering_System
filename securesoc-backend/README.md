# SecureSOC Backend — Phase 1 (Foundation)

Spring Boot 3 / Java 21 backend for the SecureSOC / LAN Realtime Monitoring System.
This phase covers: PostgreSQL schema + Flyway migrations, JWT auth (access +
refresh tokens), and the agent registration/heartbeat pipeline needed to hit
the first project milestone — **one endpoint successfully "checking in."**

## What's implemented

- **Database**: Flyway-managed schema — `departments`, `laboratories`, `roles`,
  `users`, `user_roles`, `refresh_tokens`, `endpoint_devices`
- **Auth**: `POST /api/auth/login`, `/refresh`, `/logout` — BCrypt password
  hashing, JWT access tokens (15 min default), opaque DB-backed refresh
  tokens with rotation-on-use, account lockout after 5 failed attempts
- **Agent pipeline**: `POST /api/agents/register` (shared-secret auth) and
  `POST /api/agents/heartbeat` (per-device token auth), matching
  `securesoc-agent/agent.py` and `collector.py` exactly
- **`EndpointOfflineSweeper`**: scheduled job that flips an endpoint back to
  `OFFLINE` once its heartbeat goes stale — this is what makes the "stop the
  agent, wait ~60s, see it go offline" flow in the agent's README actually work
- **`GET /api/endpoints`**: backs the frontend's dashboard once
  `VITE_USE_MOCKS=false`

## Not yet implemented (later phases)

Monitoring event ingestion (`/monitoring/**` — login/logout, running apps,
USB, VPN, idle, network/internet usage), the detection engine, risk scoring,
alerts, and the WebSocket push layer are all **Phase 3–5** work, not this
phase. The agent already sends/queues this data; there's just nothing on the
backend side consuming it yet.

## Setup

```bash
# 1. Start Postgres
docker compose up -d

# 2. Run the app (Flyway migrations run automatically on startup)
mvn spring-boot:run
```

Default connection assumes the bundled `docker-compose.yml` (db `securesoc` /
user `securesoc` / password `securesoc` on `localhost:5432`). Override via env
vars for anything else — see `application.yml` for the full list
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`AGENT_REGISTRATION_SECRET`, `CORS_ALLOWED_ORIGINS`, etc.).

**Before deploying anywhere real:** override `JWT_SECRET` and
`AGENT_REGISTRATION_SECRET` — the defaults in `application.yml` are
public (committed to source control) and only safe for local dev.

## First login

A seed admin user is created by `V3__seed_admin_user.sql`:

```
username: soc.admin
email:    soc.admin@securesoc.local
password: ChangeMe123!
```

Change this password (or delete/replace the seed row) before using this
anywhere beyond local development — the hash is public in this migration file.

## Wiring up the agent

In `securesoc-agent/config.ini`, set:

```ini
[server]
url = http://localhost:8080/api
registration_secret = dev-only-agent-registration-secret-change-me
```

(must match `AGENT_REGISTRATION_SECRET` / `securesoc.agent.registration-secret`
above — change both together). Then `python agent.py` and confirm:

```bash
curl http://localhost:8080/api/endpoints \
  -H "Authorization: Bearer <accessToken from /auth/login>"
```

You should see the machine listed with `"status": "ONLINE"`.

## Wiring up the frontend

In `frontend/.env.local`:

```ini
VITE_API_BASE_URL=http://localhost:8080/api
VITE_USE_MOCKS=false
```

The login page already calls `POST /auth/login` directly — sign in with the
seed admin above and the dashboard's endpoint list will hit `GET /endpoints`
for real instead of mock data. (Alerts/risk-score widgets stay empty until
the corresponding phases are built.)

## Project layout

```
src/main/java/com/securesoc/
  config/       JwtProperties, AgentProperties, CorsProperties, SecurityConfig
  security/     JwtService, JwtAuthenticationFilter, AgentTokenAuthFilter,
                SecurityUserDetails(Service), TokenHasher
  controller/   AuthController, AgentController, EndpointController
  service/      AuthService, AgentService, EndpointService,
                EndpointOfflineSweeper
  entity/       User, Role, Department, Laboratory, RefreshToken, EndpointDevice
  repository/   Spring Data JPA repositories
  dto/          Request/response records (mirror frontend/src/types/api.ts
                where applicable — see field-level comments)
  exception/    GlobalExceptionHandler + custom exceptions

src/main/resources/
  application.yml
  db/migration/       Flyway SQL migrations (V1, V2, V3…)
```

## A note on this build

This project was scaffolded in a sandboxed environment without access to
Maven Central, so `mvn compile` / `mvn test` could not be run to verify it
builds clean before pushing. The code was written and manually reviewed
carefully, but **run `mvn spring-boot:run` (or `mvn test`) locally first** and
treat any compiler errors as expected first-pass issues to fix, not a sign
something is fundamentally wrong with the design.
