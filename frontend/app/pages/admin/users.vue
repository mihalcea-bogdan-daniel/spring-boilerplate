<script setup lang="ts">
import type { DataTablePageEvent, DataTableSortEvent } from 'primevue/datatable'
import type { PageResponse, RoleDto, UserDto, UserStatus } from '~/types/api'

definePageMeta({ permission: 'user:read' })

const auth = useAuthStore()
const { t, d } = useI18n()
const confirm = useConfirm()
const { request, pending: saving, fieldErrors: errors } = useRequest()

// ---------------------------------------------------------------- lazy table state -> query string
const page = ref(0)
const size = ref(20)
const sort = ref('createdAt,desc')
const search = ref('')
const q = refDebounced(search, 300)

const { data, pending, error, refresh } = useApi<PageResponse<UserDto>>('/admin/users', {
  query: { page, size, sort, q },
  server: false,
})
const { data: roles } = useApi<RoleDto[]>('/admin/roles', { server: false })

function onPage(e: DataTablePageEvent) {
  page.value = e.page
  size.value = e.rows
}
function onSort(e: DataTableSortEvent) {
  sort.value = e.sortField ? `${String(e.sortField)},${e.sortOrder === 1 ? 'asc' : 'desc'}` : 'createdAt,desc'
}

const statusSeverity = (s: UserStatus) => ({ ACTIVE: 'success', PENDING: 'warn', DISABLED: 'danger' }[s])
const statuses = computed(() => (['ACTIVE', 'PENDING', 'DISABLED'] as UserStatus[]).map(s => ({ label: t(`status.${s}`), value: s })))

// ---------------------------------------------------------------- create / edit dialog
interface UserForm { id?: string, email: string, firstName: string, lastName: string, password: string, status: UserStatus, roles: string[] }
const dialog = ref(false)
const form = reactive<UserForm>({ email: '', firstName: '', lastName: '', password: '', status: 'ACTIVE', roles: ['USER'] })

function openCreate() {
  Object.assign(form, { id: undefined, email: '', firstName: '', lastName: '', password: '', status: 'ACTIVE', roles: ['USER'] })
  errors.value = {}
  dialog.value = true
}
function openEdit(u: UserDto) {
  Object.assign(form, { id: u.id, email: u.email, firstName: u.firstName ?? '', lastName: u.lastName ?? '', password: '', status: u.status, roles: [...u.roles] })
  errors.value = {}
  dialog.value = true
}

async function save() {
  const res = form.id
    ? await request(`/admin/users/${form.id}`, {
        method: 'PUT',
        body: { firstName: form.firstName, lastName: form.lastName, status: form.status, roles: form.roles },
        success: t('users.saved'),
        error: t('users.saveFailed'),
      })
    : await request('/admin/users', {
        method: 'POST',
        body: { email: form.email, firstName: form.firstName, lastName: form.lastName, password: form.password || null, roles: form.roles },
        success: t('users.created'),
        error: t('users.createFailed'),
      })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

async function resendInvite(u: UserDto) {
  await request(`/admin/users/${u.id}/invite`, { method: 'POST', success: t('users.inviteSent', { email: u.email }) })
}

// ---------------------------------------------------------------- super-admin: sign in as user
const tenantStore = useTenantStore()
const canImpersonate = computed(() => auth.isSuperAdmin && !!tenantStore.managedTenant)
async function impersonate(u: UserDto) {
  const res = await request<{ url: string }>(
    `/platform/tenants/${tenantStore.managedTenant}/impersonate/${u.id}`, { method: 'POST', success: false })
  if (res.ok) window.open(res.data.url, '_blank', 'noopener')
}

function remove(u: UserDto) {
  confirm.require({
    message: t('users.deleteConfirm', { email: u.email }),
    header: t('users.deleteHeader'),
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/users/${u.id}`, { method: 'DELETE', success: t('users.deleted') })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('users.title') }}</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('users.search')" />
        </IconField>
        <Button v-if="auth.can('user:write')" :label="t('users.new')" icon="pi pi-plus" @click="openCreate" />
      </div>
    </div>

    <LoadError :error="error" :what="t('users.what')" @retry="refresh()" />
    <DataTable
      v-if="!error"
      :value="data?.content ?? []"
      :loading="pending"
      lazy
      paginator
      :rows="size"
      :total-records="data?.totalElements ?? 0"
      :rows-per-page-options="[10, 20, 50]"
      data-key="id"
      striped-rows
      removable-sort
      @page="onPage"
      @sort="onSort"
    >
      <template #empty>{{ t('users.empty') }}</template>
      <Column field="email" :header="t('common.email')" sortable />
      <Column :header="t('common.name')">
        <template #body="{ data: u }">{{ [u.firstName, u.lastName].filter(Boolean).join(' ') || '—' }}</template>
      </Column>
      <Column field="status" :header="t('common.status')" sortable>
        <template #body="{ data: u }"><Tag :value="t(`status.${u.status}`)" :severity="statusSeverity(u.status)" /></template>
      </Column>
      <Column :header="t('common.roles')">
        <template #body="{ data: u }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Tag v-for="r in u.roles" :key="r" :value="r" severity="secondary" /></div>
        </template>
      </Column>
      <Column field="lastLoginAt" :header="t('users.lastLogin')" sortable>
        <template #body="{ data: u }">{{ u.lastLoginAt ? d(u.lastLoginAt, 'datetime') : '—' }}</template>
      </Column>
      <Column v-if="auth.can('user:write')" header="" style="width: 13rem; text-align: right">
        <template #body="{ data: u }">
          <Button
v-if="canImpersonate && u.status === 'ACTIVE'" v-tooltip.top="t('users.impersonate')" icon="pi pi-user-edit"
                  text rounded :aria-label="t('users.impersonate')" @click="impersonate(u)" />
          <Button
v-if="u.status === 'PENDING'" v-tooltip.top="t('users.resendInvite')" icon="pi pi-send" text rounded
                  :aria-label="t('users.resendInvite')" @click="resendInvite(u)" />
          <Button icon="pi pi-pencil" text rounded :aria-label="t('common.edit')" @click="openEdit(u)" />
          <Button icon="pi pi-trash" text rounded severity="danger" :aria-label="t('common.delete')" @click="remove(u)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="form.id ? t('users.edit') : t('users.new')" modal :style="{ width: '32rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="u-email">{{ t('common.email') }}</label>
          <InputText id="u-email" v-model="form.email" type="email" :disabled="!!form.id" :invalid="!!errors.email" required fluid />
          <small v-if="errors.email" class="error">{{ errors.email }}</small>
        </div>
        <div class="grid-2">
          <div class="field">
            <label for="u-fn">{{ t('common.firstName') }}</label>
            <InputText id="u-fn" v-model="form.firstName" fluid />
          </div>
          <div class="field">
            <label for="u-ln">{{ t('common.lastName') }}</label>
            <InputText id="u-ln" v-model="form.lastName" fluid />
          </div>
        </div>
        <div v-if="!form.id" class="field">
          <label for="u-pw">{{ t('users.initialPassword') }} <span class="muted">{{ t('users.initialPasswordHint') }}</span></label>
          <Password id="u-pw" v-model="form.password" toggle-mask :invalid="!!errors.password" fluid />
          <small v-if="errors.password" class="error">{{ errors.password }}</small>
        </div>
        <div v-else class="field">
          <label for="u-status">{{ t('common.status') }}</label>
          <Select id="u-status" v-model="form.status" :options="statuses" option-label="label" option-value="value" fluid />
        </div>
        <div class="field">
          <label for="u-roles">{{ t('common.roles') }}</label>
          <MultiSelect id="u-roles" v-model="form.roles" :options="roles ?? []" option-label="name" option-value="name" display="chip" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="dialog = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
