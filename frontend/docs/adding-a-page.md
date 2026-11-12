# Adding a page

Example: a "Projects" admin screen backed by `/api/admin/projects`.

## 1. Backend first

The endpoint, its permission (`project:read` / `project:write`, seeded in a tenant Flyway migration) and the
DTO record exist and have an integration test. See the backend README.

## 2. Type

`app/types/api.ts`:

```ts
export interface ProjectDto {
  id: string
  name: string
  createdAt: string
}
```

## 3. Page

`app/pages/admin/projects.vue`. The file path is the route (`/admin/projects`).

```vue
<script setup lang="ts">
import type { PageResponse, ProjectDto } from '~/types/api'

definePageMeta({ permission: 'project:read' })

const auth = useAuthStore()
const confirm = useConfirm()
const { request, pending: saving, fieldErrors } = useRequest()

const page = ref(0)
const size = ref(20)
const { data, pending, error, refresh } = useApi<PageResponse<ProjectDto>>('/admin/projects', {
  query: { page, size, sort: 'createdAt,desc' }, server: false,
})

const dialog = ref(false)
const form = reactive({ name: '' })

async function save() {
  const res = await request('/admin/projects', {
    method: 'POST', body: form, success: 'Project created', error: 'Could not create the project',
  })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

function remove(p: ProjectDto) {
  confirm.require({
    header: 'Delete project',
    message: `Delete ${p.name}?`,
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/projects/${p.id}`, { method: 'DELETE', success: 'Project deleted' })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">Projects</h1>
      <Button v-if="auth.can('project:write')" label="New project" icon="pi pi-plus" @click="dialog = true" />
    </div>

    <LoadError :error="error" what="projects" @retry="refresh()" />
    <DataTable v-if="!error" :value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
               :total-records="data?.totalElements ?? 0" data-key="id"
               @page="e => { page = e.page; size = e.rows }">
      <template #empty>No projects yet.</template>
      <Column field="name" header="Name" />
      <Column v-if="auth.can('project:write')" header="" style="width: 4rem; text-align: right">
        <template #body="{ data: p }">
          <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Delete" @click="remove(p)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" header="New project" modal :style="{ width: '28rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="p-name">Name</label>
          <InputText id="p-name" v-model="form.name" :invalid="!!fieldErrors.name" required fluid />
          <small v-if="fieldErrors.name" class="error">{{ fieldErrors.name }}</small>
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="dialog = false" />
          <Button type="submit" label="Save" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
```

## 4. Menu

`app/layouts/default.vue`, inside `admin`:

```ts
{ label: 'Projects', icon: 'pi pi-folder', route: '/admin/projects', visible: auth.can('project:read') },
```

## 5. Check

```bash
npm run lint && npm run typecheck && npm test && npm run build
```

Then open the page with a user that lacks `project:write` and confirm the write buttons are gone, and stop the
backend to confirm `<LoadError>` appears instead of "No projects yet".
