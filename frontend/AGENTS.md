# Frontend rules for agents

Nuxt 4, Vue 3 `<script setup lang="ts">`, strict TypeScript, Pinia, PrimeVue 4 (Aura preset, auto-imported).
Longer explanations and copy-paste examples live in [`docs/`](docs/README.md). Read that before your first change.

**Reference page:** [`app/pages/admin/users.vue`](app/pages/admin/users.vue). New admin pages copy its shape.

## Hard rules

1. **Never call `$fetch` or `fetch` directly.** The `$api` client adds the bearer token, the tenant header and
   refresh-on-401. Raw fetch breaks multi-tenancy.
2. **Loading data for display → `useApi()`**, and render `<LoadError :error="error" @retry="refresh()" />`
   next to it. Hide the table/form while `error` is set. An error is never shown as "No items found".
3. **Anything the user clicks (save, delete, invite…) → `useRequest().request()`.** It toasts the outcome,
   fills field errors and tracks `pending`. Do not write `try/catch` + `toast.add` around `$api`.
4. **Toast severities are decided by `utils/feedback.ts`, not per page.** Pass a `success` / `error`
   summary; do not pick colours yourself. `success: false` when the UI already shows the result.
5. **Every page declares access** with `definePageMeta({ permission: 'thing:read' })`,
   `{ superAdminOnly: true }` or `{ public: true }`. Buttons that write check `auth.can('thing:write')`.
   The backend enforces the same permission with `@PreAuthorize`; the UI check is cosmetic.
6. **Use PrimeVue components** (table below). No other UI kit, no hand-made buttons, inputs, modals or toasts.
7. **Colours come from tokens**: `var(--p-primary-color)`, `var(--p-surface-*)`, `var(--p-text-color)`,
   `var(--p-text-muted-color)`. No hex values in pages. Brand colours are defined once in `app/theme/`.
8. **Layout uses the utility classes in `assets/css/main.css`**: `stack`, `row`, `row-between`, `grid-2`,
   `field`, `muted`, `page-title`. Don't add Tailwind or a second CSS framework.
9. **Types**: DTOs come from `~/types/api`. Add new ones there next to the endpoint's Java record. No `any`.
10. **Do not edit** `plugins/api.ts`, `stores/auth.ts`, `stores/tenant.ts`, `middleware/auth.global.ts`
    unless the task is about auth or tenancy itself. Never send `X-Tenant-ID` yourself.
11. **Destructive actions** go through `useConfirm().require({ … acceptProps: { severity: 'danger' } })`.
12. **New sidebar entries** go in `layouts/default.vue` with `visible: auth.can('…')`.

## Components

| Need | Use |
| --- | --- |
| Button / icon button | `Button` (`text rounded` for row icons, always with `aria-label`) |
| List with paging/sort | `DataTable` + `Column`, `lazy`, server paging via `page`/`size`/`sort` refs |
| Form fields | `InputText`, `Password`, `Textarea`, `Select`, `MultiSelect`, `Checkbox`, `ToggleSwitch`, `InputOtp` |
| Search box | `IconField` + `InputIcon` + `InputText`, debounced with `refDebounced(search, 300)` |
| Create / edit | `Dialog` (`modal`) wrapping a `<form class="stack" @submit.prevent>` |
| Status pill | `Tag` with `severity` |
| Page section | `Card` |
| Inline notice | `Message` (`:closable="false"`) |
| Load failure | `LoadError` (ours) |
| Confirmation | `useConfirm()` (the `ConfirmDialog` is mounted in `app.vue`) |
| Feedback | `useRequest()`; `useToast()` only for local actions such as "Copied" |
| Icons | PrimeIcons, `pi pi-*` |

## Smallest correct mutation

```ts
const { request, pending, fieldErrors } = useRequest()

async function save() {
  const res = await request<ThingDto>('/admin/things', {
    method: 'POST', body: form, success: 'Thing created', error: 'Could not create the thing',
  })
  if (!res.ok) return          // toast and fieldErrors are already handled
  dialog.value = false
  await refresh()
}
```

```vue
<InputText v-model="form.name" :invalid="!!fieldErrors.name" fluid />
<small v-if="fieldErrors.name" class="error">{{ fieldErrors.name }}</small>
<Button type="submit" label="Save" :loading="pending" />
```

## Before you finish

```bash
npm run lint && npm run typecheck && npm test && npm run build
```
