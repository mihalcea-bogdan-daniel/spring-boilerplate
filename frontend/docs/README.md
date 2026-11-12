# Frontend guide

For people and coding agents working in `frontend/`. The short, enforceable version is [`../AGENTS.md`](../AGENTS.md).

| Guide | Read it when you… |
| --- | --- |
| [Data and errors](data-and-errors.md) | load a list, save a form, or wonder which toast colour to show |
| [Adding a page](adding-a-page.md) | add a new admin screen end to end |
| [Theming and PWA](theming-and-pwa.md) | touch colours, the logo, the manifest or offline behaviour |

## How the app is put together

```
app/
  app.vue                 Toast + ConfirmDialog mounted once, dark-mode class
  layouts/default.vue     dashboard shell: sidebar menu, tenant switcher, user menu
  layouts/auth.vue        centred card for sign-in screens
  middleware/auth.global  session + definePageMeta({ permission | superAdminOnly | public })
  plugins/api.ts          $api: bearer, X-Tenant-ID, SSR host forwarding, refresh on 401
  plugins/branding.ts     per-tenant name, colour, logo
  composables/useApi      useFetch bound to $api           → data on screen
  composables/useRequest  request() with toasts + field errors → user actions
  components/LoadError    error state with retry for useApi
  utils/feedback.ts       HTTP status → toast severity (single source of truth)
  utils/problem.ts        problemOf(error) → RFC 7807 body
  theme/                  brand palette and PrimeVue preset
  stores/                 auth (session, permissions) and tenant (managed tenant)
  types/api.ts            DTOs shared with the backend
```

## Requests in one picture

```
page ──useApi()──────┐
page ──request()─────┼──► $api ──► /api/** (nginx) ──► Spring
store ──$api()───────┘     │
                           ├─ Authorization: Bearer <access token>
                           ├─ X-Tenant-ID (only when a super-admin manages another tenant)
                           └─ 401 → refresh once → replay, or go to /auth/login
```

The browser always calls the same origin (`/api`). The host name (`acme.example.com`) selects the tenant, so
pages never deal with tenants explicitly.
