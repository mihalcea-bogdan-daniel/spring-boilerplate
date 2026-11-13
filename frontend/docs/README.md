# Frontend guide

For people and coding agents working in `frontend/`. The short, enforceable version is [`../AGENTS.md`](../AGENTS.md).

| Guide | Read it when you… |
| --- | --- |
| [Data and errors](data-and-errors.md) | load a list, save a form, or wonder which toast colour to show |
| [Adding a page](adding-a-page.md) | add a new admin screen end to end |
| [Organisation](organisation.md) | work with units, ranks, supervision or "sign in as" |
| [Ownership and access](../../backend/docs/access.md) | build anything that someone owns or shares |
| [Translations (i18n)](i18n.md) | write any text a user will see, add a key or a language |
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
  components/LocaleSwitcher  language picker (cookie i18n_locale)
  composables/useLocale   <html lang> + PrimeVue texts follow the locale
  theme/                  brand palette and PrimeVue preset
  stores/                 auth (session, permissions) and tenant (managed tenant)
  types/api.ts            DTOs shared with the backend
i18n/
  locales/en.json, ro.json  every UI string (en is the source of truth)
  i18n.config.ts          date formats
  primevue.ts             PrimeVue's built-in texts per locale
```

## Requests in one picture

```
page ──useApi()──────┐
page ──request()─────┼──► $api ──► /api/** (nginx) ──► Spring
store ──$api()───────┘     │
                           ├─ Authorization: Bearer <access token>
                           ├─ X-Tenant-ID (only when a super-admin manages another tenant)
                           ├─ Accept-Language: <current locale>
                           └─ 401 → refresh once → replay, or go to /auth/login
```

The browser always calls the same origin (`/api`). The host name (`acme.example.com`) selects the tenant, so
pages never deal with tenants explicitly.
