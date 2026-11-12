<script setup lang="ts">
import type { PermissionDto, RoleDto } from '~/types/api'

definePageMeta({ permission: 'role:read' })

const auth = useAuthStore()
const confirm = useConfirm()
const { request, pending: saving } = useRequest()

const { data: roles, pending, error, refresh } = useApi<RoleDto[]>('/admin/roles', { server: false })
const { data: permissions } = useApi<PermissionDto[]>('/admin/permissions', { server: false })

const dialog = ref(false)
const form = reactive<{ id?: string, name: string, description: string, permissions: string[], systemRole: boolean }>(
  { name: '', description: '', permissions: [], systemRole: false })

function open(role?: RoleDto) {
  Object.assign(form, role
    ? { id: role.id, name: role.name, description: role.description ?? '', permissions: [...role.permissions], systemRole: role.systemRole }
    : { id: undefined, name: '', description: '', permissions: [], systemRole: false })
  dialog.value = true
}

async function save() {
  const body = { name: form.name, description: form.description, permissions: form.permissions }
  const res = await request(form.id ? `/admin/roles/${form.id}` : '/admin/roles', {
    method: form.id ? 'PUT' : 'POST', body, success: 'Role saved', error: 'Could not save the role',
  })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

function remove(role: RoleDto) {
  confirm.require({
    header: 'Delete role',
    message: `Delete role ${role.name}?`,
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/roles/${role.id}`, { method: 'DELETE', success: `Role ${role.name} deleted` })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">Roles &amp; permissions</h1>
      <Button v-if="auth.can('role:write')" label="New role" icon="pi pi-plus" @click="open()" />
    </div>
    <LoadError :error="error" what="roles" @retry="refresh()" />
    <DataTable v-if="!error" :value="roles ?? []" :loading="pending" data-key="id">
      <template #empty>No roles.</template>
      <Column field="name" header="Role">
        <template #body="{ data: r }">
          <span class="row">{{ r.name }} <Tag v-if="r.systemRole" value="system" severity="secondary" /></span>
        </template>
      </Column>
      <Column field="description" header="Description" />
      <Column header="Permissions">
        <template #body="{ data: r }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Chip v-for="p in r.permissions" :key="p" :label="p" /></div>
        </template>
      </Column>
      <Column v-if="auth.can('role:write')" style="width: 7rem; text-align: right">
        <template #body="{ data: r }">
          <Button icon="pi pi-pencil" text rounded aria-label="Edit" @click="open(r)" />
          <Button v-if="!r.systemRole" icon="pi pi-trash" text rounded severity="danger" aria-label="Delete" @click="remove(r)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="form.id ? 'Edit role' : 'New role'" modal :style="{ width: '34rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="r-name">Name</label>
          <InputText id="r-name" v-model="form.name" :disabled="form.systemRole" placeholder="BILLING_MANAGER" required fluid />
        </div>
        <div class="field">
          <label for="r-desc">Description</label>
          <Textarea id="r-desc" v-model="form.description" rows="2" auto-resize fluid />
        </div>
        <div class="field">
          <label>Permissions</label>
          <div v-for="p in permissions ?? []" :key="p.code" class="row">
            <Checkbox v-model="form.permissions" :input-id="`perm-${p.code}`" :value="p.code" />
            <label :for="`perm-${p.code}`"><code>{{ p.code }}</code> <span class="muted">{{ p.description }}</span></label>
          </div>
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="dialog = false" />
          <Button type="submit" label="Save" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
