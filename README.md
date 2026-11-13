# Multi-tenant Enterprise Starter

Spring Boot 3.5 (Java 17+) · PostgreSQL (schema-per-tenant) · Nuxt 4 · PrimeVue 4 (Aura) · Pinia

**Guides:** setup site in [`docs/index.html`](docs/index.html) (publish with GitHub Pages → *Deploy from branch* →
`main` / `/docs`) · frontend rules for contributors and coding agents in
[`frontend/AGENTS.md`](frontend/AGENTS.md) and [`frontend/docs/`](frontend/docs/README.md)

```
spring-boilerplate/
├── backend/                                   Spring Boot API + Thymeleaf
│   ├── build.gradle  settings.gradle  gradlew  Dockerfile
│   └── src/main/
│       ├── java/com/polygis/
│       │   ├── tenancy/      ← multi-tenancy core
│       │   │   ├── TenancyConfig.java                  Hibernate + filter wiring, migrate-before-JPA
│       │   │   ├── SchemaMultiTenantConnectionProvider  connection.setSchema(t_<slug>)
│       │   │   ├── TenantIdentifierResolver             TenantContext -> Hibernate
│       │   │   ├── TenantResolutionFilter / HostTenantResolver   Host/sub-domain/custom domain -> tenant
│       │   │   ├── TenantAccessFilter                   JWT tenant check + super-admin X-Tenant-ID switch
│       │   │   ├── TenantRegistry                       cached view of public.tenants
│       │   │   └── TenantSchemaMigrator                 Flyway: public + every tenant schema
│       │   ├── auth/         ← login, register, OTP, refresh rotation, JWT
│       │   │   └── oauth2/   ← signed-cookie state, tenant-aware social login, code exchange
│       │   ├── user/         ← users, roles, permissions (RBAC) admin API
│       │   ├── platform/     ← tenant provisioning (super-admin), bootstrap super-admin
│       │   ├── settings/     ← per-tenant key/value settings
│       │   ├── mail/         ← Thymeleaf e-mail rendering
│       │   ├── web/          ← Thymeleaf views, RFC 7807 error handler
│       │   └── config/       ← SecurityConfig, AppProperties, async tenant propagation
│       └── resources/
│           ├── application.yml
│           ├── db/migration/public/   registry (runs once)
│           ├── db/migration/tenant/   identity, RBAC, tokens, settings (runs per tenant)
│           └── templates/             status.html, oauth2-error.html, email/*.html
├── frontend/                                  Nuxt 4 (app/ directory layout)
│   ├── nuxt.config.ts                         PrimeVue Aura preset, Pinia, dev proxy, runtimeConfig
│   └── app/
│       ├── plugins/api.ts          ← $api: Bearer + X-Tenant-ID + SSR host forwarding + 401 refresh/retry
│       ├── stores/auth.ts          ← session, login/OTP/OAuth2 flows, permission checks
│       ├── stores/tenant.ts        ← super-admin "managed tenant"
│       ├── middleware/auth.global.ts  ← public / permission / superAdminOnly page meta
│       ├── composables/useApi.ts   ← useFetch bound to $api
│       ├── layouts/{auth,default}.vue   ← auth card / dashboard shell with tenant switcher
│       └── pages/
│           ├── auth/{login,register,verify-email,otp,forgot-password,callback}.vue
│           ├── admin/{users,roles,settings}.vue     PrimeVue DataTable + Dialog forms
│           └── platform/tenants.vue                 provisioning + "manage tenant"
├── infra/nginx/default.conf   infra/apache/tenant-vhost.conf
├── docker-compose.yml   .env.example
```

## What's included beyond the core

| Area | Feature |
|---|---|
| Account | Profile, set/change password, active sessions with revoke, TOTP two-factor with recovery codes, passkeys |
| Admin | User invitations, soft delete, audit log, API keys (`X-API-Key`), per-tenant branding |
| Platform | Plans and user quotas (`app.plans.*`), tenant status (schema version, size, usage), offboarding, "sign in as user" |
| Security | Auth rate limiting, breached-password check, security headers, optional RS256 + JWKS |
| Operations | Sentry error reporting (backend + Nuxt, opt-in via DSN), LISTEN/NOTIFY registry sync, parallel tenant migrations, per-tenant background jobs, request/tenant/user ids in logs |
| Quality | Integration tests on embedded PostgreSQL, ArchUnit rules, ESLint, Vitest, generated API types, GitHub Actions CI |

Quality gates: `cd backend && ./gradlew build` and `cd frontend && npm run lint && npm run typecheck && npm test`.

## How a request finds its tenant

```
browser ── https://acme.example.com/api/admin/users ──► nginx (Host, X-Forwarded-Host preserved)
   └─► Spring
        1. ForwardedHeaderFilter          request.getServerName() = acme.example.com
        2. TenantResolutionFilter         custom domain? → <slug>.<base-domain>? → TenantContext = acme
        3. BearerTokenAuthenticationFilter  validates JWT (HS256, typ=access)
        4. TenantAccessFilter             token.tenant must equal host tenant …
                                          … unless token is SUPER_ADMIN from the platform tenant:
                                              X-Tenant-ID: globex  → TenantContext = globex
        5. @PreAuthorize("hasAuthority('user:read')")
        6. Hibernate opens session → TenantIdentifierResolver → "t_acme"
           → SchemaMultiTenantConnectionProvider: connection.setSchema("t_acme"), reset to public on release
```

| Host | Tenant |
|---|---|
| `acme.example.com` | `acme` (schema `t_acme`) |
| `portal.customer.com` | tenant whose `domain` column matches |
| `admin.example.com` | `platform` — home of super-admins |
| anything else | `404 Unknown tenant` (except `/actuator`, `/api/docs`, OAuth2 callback, static) |

**Super-admins** sign in on `admin.example.com`. Their JWT has `tenant=platform` and role `SUPER_ADMIN`.
In the dashboard they pick a tenant in the top-bar switcher (or *Tenants → Manage*), and the `$api` plugin
adds `X-Tenant-ID: <slug>` to every non-`/auth` call. The backend honours the header **only** for platform
super-admins; any other caller sending a mismatching header gets `403`. `/api/auth/me` always loads the profile
from the token's own tenant, so switching never breaks the session.

## Authentication

| Flow | Endpoints |
|---|---|
| Register → e-mail OTP → auto sign-in | `POST /api/auth/register`, `/verify-email`, `/verify-email/resend` |
| Password (BCrypt 12, lockout after 5 failures) | `POST /api/auth/login` |
| Passwordless e-mail OTP | `POST /api/auth/otp/request`, `/otp/verify` |
| Password reset via OTP (revokes all sessions) | `POST /api/auth/password/forgot`, `/password/reset` |
| OAuth2 (Google, GitHub) | `GET /api/oauth2/authorization/{google\|github}?redirect=/path` → provider → `/api/login/oauth2/code/*` → SPA `/auth/callback?code=…` → `POST /api/auth/oauth2/exchange` |
| Session | `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me` |

* **Access token** – 15-min HS256 JWT with `tenant`, `roles`, `perms`. Returned in the body; the SPA keeps it in
  a `SameSite=Strict` cookie so SSR can use it.
* **Refresh token** – opaque, SHA-256 hashed in the tenant schema, `HttpOnly; SameSite=Strict; Path=/api/auth`,
  host-only (never shared between tenant sub-domains). Rotated on every use; replaying a rotated token revokes
  the whole token family.
* **OTP codes** – 6 digits, 10-min TTL, 5 attempts, 60 s resend cooldown, stored as `sha256(userId:code)`.
  Endpoints that take an e-mail always answer `202` to avoid account enumeration.
* **OAuth2 state** lives in an HMAC-signed cookie (no HTTP session) that also carries the tenant, so the callback
  works even on a central callback host. Tokens never appear in URLs: the SPA receives a 60-second one-time code.

### Passkeys

Users add passkeys from their profile and then use **Sign in with a passkey** on the login page (discoverable
credentials: no e-mail needed). The relying party is the workspace host, so a passkey created on
`acme.example.com` only works there. User verification (biometrics or device PIN) is required, so a passkey
sign-in skips the TOTP step. Ceremonies are stateless: challenges are one-time tokens in the tenant schema.
Verification uses [webauthn4j](https://github.com/webauthn4j/webauthn4j) (0.30.x: the 0.31 line needs Jackson 3,
which Spring Boot 3.5 does not ship).

### OAuth2 redirect URIs

*Per-tenant (default)*: register `https://<slug>.example.com/api/login/oauth2/code/google` for each tenant —
fine for a few tenants, but Google/GitHub do not accept wildcards.

*Central callback host (recommended at scale)*: route `auth.example.com` to the backend, then set
`OAUTH2_REDIRECT_BASE=https://auth.example.com` and `OAUTH2_COOKIE_DOMAIN=.example.com`. Register only
`https://auth.example.com/api/login/oauth2/code/{provider}`. The tenant comes back inside the signed cookie and
the user is redirected to `https://<their tenant host>/auth/callback`.

GitHub only returns a public e-mail in `/user`, so `GitHubEmailOAuth2UserService` asks `/user/emails` for the
primary verified address (scope `user:email`). Unverified addresses are never accepted.

## RBAC

Permissions (`user:read`, `user:write`, `role:read`, `role:write`, `settings:read`, `settings:write`) are
seeded in every tenant schema. `TENANT_ADMIN` gets all of them, `USER` none. Custom roles are created from
*Administration → Roles*. In the platform schema `SuperAdminBootstrap` creates `SUPER_ADMIN` with every
permission plus `tenant:manage`. Use `@PreAuthorize("hasAuthority('…')")` in Spring and
`definePageMeta({ permission: '…' })` / `auth.can('…')` in Nuxt.

## Tenant provisioning

`POST /api/platform/tenants` (super-admin):
1. insert `public.tenants` row as `PROVISIONING`
2. `CREATE SCHEMA t_<slug>` + Flyway `db/migration/tenant`
3. create the first `TENANT_ADMIN` (signs in with an e-mail code)
4. mark `ACTIVE`, refresh the registry, e-mail the invitation

At startup every non-provisioning tenant schema is migrated **before** the `EntityManagerFactory` is built, so a
new tenant migration file rolls out to all tenants on the next deploy. `POST /api/platform/tenants/{id}/migrate`
retries a single tenant.

## Running locally

Requirements: JDK 17+ (21 recommended), Node 22+, PostgreSQL 16 and an SMTP catcher (both in compose).

```bash
docker compose up -d postgres mailpit
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

Want data to click through? Start the backend with demo tenants `acme` and `globex` (six users each,
`admin@acme.test` / `Demo-Passw0rd!`):

```bash
cd backend && ./gradlew bootRun --args='--app.seed=true'
```

* Super-admin: http://admin.localhost:3000 — `superadmin@admin.localhost` / `ChangeMe!123` (dev profile defaults)
* Provision a tenant `acme` from *Platform → Tenants*, then open http://acme.localhost:3000. Sign-up adds a user
  to an existing workspace; a host with no tenant shows "No workspace here".
* Read e-mails (OTP codes, invites) at http://localhost:8025
* Swagger UI: http://localhost:8080/api/docs/ui (with header `X-Tenant-ID: platform` thanks to the dev fallback)

In dev, Nuxt proxies `/api` to `:8080` **keeping the Host header**, which is exactly what nginx does in
production. The `dev` profile also enables `dev-header-fallback`, letting anonymous calls pick a tenant with
`X-Tenant-ID` when the host carries none (e.g. `curl localhost:8080`).

Full stack behind nginx:

```bash
cp .env.example .env && docker compose up --build
```

then open http://admin.localhost. `acme.localhost` works once the tenant exists: provision it from
*Platform → Tenants*, or set `APP_SEED=true` in `.env` and restart the backend to get the demo tenants
`acme` and `globex`.

## Keeping frontend types in sync with the API

Every backend test run writes the live OpenAPI document to `backend/build/openapi.json`. Regenerate the
TypeScript definitions from it after changing a controller or DTO:

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm run gen:api
```

`app/types/api.generated.ts` (generated, committed) contains every path and schema. The hand-written
`app/types/api.ts` is still what the pages import; move a type over by re-exporting it, e.g.
`export type UserDto = Required<components['schemas']['UserDto']>`.

## Error reporting (Sentry)

Set `SENTRY_DSN` (backend) and `NUXT_PUBLIC_SENTRY_DSN` (browser + SSR) to turn it on; without them the SDKs
stay idle. The backend reports ERROR log events (unhandled 500s, failed jobs, mail give-ups), never expected
4xx, tagged with `requestId`, `tenant` and the user id. The frontend tags the tenant and user id and collects no
cookies, headers, bodies or query strings (`app/utils/sentry-privacy.ts`). Source maps are uploaded during
`npm run build` only when `SENTRY_AUTH_TOKEN`, `SENTRY_ORG` and `SENTRY_PROJECT` are set.

## Production checklist

- [ ] `JWT_SECRET` ≥ 32 random bytes from a secret store. If other services verify tokens, set `JWT_ALGORITHM=RS256` and `JWT_PRIVATE_KEY` (PKCS#8 PEM); the public key is served at `/api/auth/jwks.json`
- [ ] Backend reachable **only** from nginx and the Nuxt server — it trusts `X-Forwarded-Host` for tenant resolution
- [ ] Wildcard TLS certificate (DNS-01) for `*.example.com`; custom domains need their own certs
- [ ] Rate-limit `/api/auth/**` at nginx (`limit_req`) in addition to the built-in OTP/lockout limits
- [ ] Back up `public` and all `t_*` schemas; `pg_dump -n 't_*'`
- [ ] Bump the toolchain in `build.gradle` to 21 and set `spring.threads.virtual.enabled=true`
