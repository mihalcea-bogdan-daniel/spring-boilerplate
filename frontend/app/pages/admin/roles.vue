<script setup lang="ts">
import type { PermissionDto, RoleDto } from '~/types/api'

definePageMeta({ permission: 'role:read' })

const auth = useAuthStore()
const { t } = useI18n()
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
    method: form.id ? 'PUT' : 'POST', body, success: t('roles.saved'), error: t('roles.saveFailed'),
  })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

function remove(role: RoleDto) {
  confirm.require({
    header: t('roles.deleteHeader'),
    message: t('roles.deleteConfirm', { name: role.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/roles/${role.id}`, { method: 'DELETE', success: t('roles.deleted', { name: role.name }) })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('roles.title') }}</h1>
      <Button v-if="auth.can('role:write')" :label="t('roles.new')" icon="pi pi-plus" @click="open()" />
    </div>
    <LoadError :error="error" :what="t('roles.what')" @retry="refresh()" />
    <DataTable v-if="!error" :value="roles ?? []" :loading="pending" data-key="id">
      <template #empty>{{ t('roles.empty') }}</template>
      <Column field="name" :header="t('roles.role')">
        <template #body="{ data: r }">
          <span class="row">{{ r.name }} <Tag v-if="r.systemRole" :value="t('common.system')" severity="secondary" /></span>
        </template>
      </Column>
      <Column field="description" :header="t('common.description')" />
      <Column :header="t('common.permissions')">
        <template #body="{ data: r }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Chip v-for="p in r.permissions" :key="p" :label="p" /></div>
        </template>
      </Column>
      <Column v-if="auth.can('role:write')" style="width: 7rem; text-align: right">
        <template #body="{ data: r }">
          <Button icon="pi pi-pencil" text rounded :aria-label="t('common.edit')" @click="open(r)" />
          <Button v-if="!r.systemRole" icon="pi pi-trash" text rounded severity="danger" :aria-label="t('common.delete')" @click="remove(r)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="form.id ? t('roles.edit') : t('roles.new')" modal :style="{ width: '34rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="r-name">{{ t('common.name') }}</label>
          <InputText id="r-name" v-model="form.name" :disabled="form.systemRole" placeholder="BILLING_MANAGER" required fluid />
        </div>
        <div class="field">
          <label for="r-desc">{{ t('common.description') }}</label>
          <Textarea id="r-desc" v-model="form.description" rows="2" auto-resize fluid />
        </div>
        <div class="field">
          <label>{{ t('common.permissions') }}</label>
          <div v-for="p in permissions ?? []" :key="p.code" class="row">
            <Checkbox v-model="form.permissions" :input-id="`perm-${p.code}`" :value="p.code" />
            <label :for="`perm-${p.code}`"><code>{{ p.code }}</code> <span class="muted">{{ p.description }}</span></label>
          </div>
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="dialog = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
