# Data and errors

There are exactly two ways to talk to the API from a page. Pick by *who started the request*.

| Started by | Use | Failure is shown as |
| --- | --- | --- |
| The page opening / a filter changing | `useApi()` | `<LoadError>` in place of the content |
| The user clicking something | `useRequest().request()` | a toast (and field errors on forms) |

Stores (`stores/auth.ts`) call `$api` directly and let errors bubble; the page catches them with
`notifyError(e, '…')` from `useRequest()`.

## Showing data: `useApi()` + `<LoadError>`

```ts
const page = ref(0)
const size = ref(20)
const sort = ref('createdAt,desc')
const q = refDebounced(search, 300)

const { data, pending, error, refresh } = useApi<PageResponse<ThingDto>>('/admin/things', {
  query: { page, size, sort, q },   // refs: changing one refetches
  server: false,                    // admin pages render client-side
})
```

```vue
<LoadError :error="error" what="things" @retry="refresh()" />
<DataTable v-if="!error" :value="data?.content ?? []" :loading="pending" lazy paginator
           :rows="size" :total-records="data?.totalElements ?? 0" @page="onPage" @sort="onSort">
  <template #empty>No things yet.</template>
  …
</DataTable>
```

Rules:

- **Empty and failed are different states.** Hide the table while `error` is set; `#empty` is only for a
  successful, empty response.
- Forms that load existing values (settings) also hide behind `v-if="!error"`; otherwise the user saves defaults
  over real data.
- The backend caps `size` at 100 and only sorts on whitelisted columns; an unknown sort is a 400.

## Doing things: `useRequest()`

```ts
const { request, pending, fieldErrors, notifyError } = useRequest()

const res = await request<ThingDto>('/admin/things', {
  method: 'POST',
  body: form,
  success: t => `${t.name} created`,   // string, function of the response, or false
  error: 'Could not create the thing', // toast title; the server's detail goes underneath
})
if (res.ok) { /* res.data is ThingDto */ } else { /* res.problem, res.status */ }
```

- It **never throws**. Check `res.ok`.
- `pending` is true while any request from *this* `useRequest()` instance runs. Create one instance per
  independent button/dialog if they must spin separately.
- `fieldErrors` is reset on every call and filled from the problem's `errors` map
  (`{ email: 'must be a well-formed e-mail address' }`).
- All `$fetch` options work (`query`, `headers`, `body`…). `method` is required for writes.

### Which toast appears

Decided in [`app/utils/feedback.ts`](../app/utils/feedback.ts); covered by `tests/feedback.test.ts`.

| Outcome | Severity | Default title | Body |
| --- | --- | --- | --- |
| GET / HEAD succeeded | – (no toast) | | |
| POST succeeded | `success` | Done | |
| PUT / PATCH succeeded | `success` | Saved | |
| DELETE succeeded | `success` | Deleted | |
| 202 Accepted | `info` | Request accepted | |
| 400 / 422 | `warn` | Check the form | "Some fields need your attention." when field errors exist |
| 403 | `warn` | Not allowed | server detail |
| 404 | `warn` | Not found | server detail |
| 409 | `warn` | Conflict | server detail |
| 429 | `warn` | Too many requests | "Try again in N seconds." from `Retry-After` |
| 401 | – (no toast) | | the api plugin refreshed the session or redirected to sign-in |
| 5xx | `error` | Something went wrong on our side | detail + `Reference: <requestId>` |
| No response (offline, DNS) | `error` | Can't reach the server | |

The idea: **warn = the user can fix it, error = we (or the network) broke**. Your `error:` option replaces
only the title. Toast lifetimes grow with severity (3 s → 8 s).

### When to pass `success: false`

When the screen already shows the result: a dialog that switches to "here is your new key", an action that
opens a new tab, a sign-out that navigates away.

## The problem body

Every API error is RFC 7807 JSON:

```json
{ "status": 409, "title": "Conflict", "detail": "A user with this e-mail already exists",
  "code": "conflict", "requestId": "6f1c…", "errors": { "email": "…" } }
```

`problemOf(error)` returns it with a guaranteed `detail`. Branch on `code` (stable), never on `detail` text.

## Don't

```ts
// ✗ raw fetch: no token, no tenant, no refresh
await fetch('/api/admin/things')

// ✗ hand-rolled feedback: inconsistent severities, forgets 401 and field errors
try { await $api('/admin/things', { method: 'POST', body }) }
catch (e) { toast.add({ severity: 'error', summary: problemOf(e).detail }) }

// ✗ swallowing load errors
const { data } = useApi('/admin/things')   // and no <LoadError>
```
