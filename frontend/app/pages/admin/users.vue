<script setup lang="ts">
import type { DataTablePageEvent, DataTableSortEvent } from 'primevue/datatable'
import type { PageResponse, RoleDto, UserDto, UserStatus } from '~/types/api'

definePageMeta({ permission: 'user:read' })

const auth = useAuthStore()
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
const statuses: UserStatus[] = ['ACTIVE', 'PENDING', 'DISABLED']

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
        success: 'User saved',
        error: 'Could not save the user',
      })
    : await request('/admin/users', {
        method: 'POST',
        body: { email: form.email, firstName: form.firstName, lastName: form.lastName, password: form.password || null, roles: form.roles },
        success: 'User created',
        error: 'Could not create the user',
      })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

async function resendInvite(u: UserDto) {
  await request(`/admin/users/${u.id}/invite`, { method: 'POST', success: `Invitation sent to ${u.email}` })
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
    message: `Delete ${u.email}? This cannot be undone.`,
    header: 'Delete user',
    icon: 'pi pi-exclamation-triangle',
    acceptProps: { label: 'Delete', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/users/${u.id}`, { method: 'DELETE', success: 'User deleted' })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">Users</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" placeholder="Search users" />
        </IconField>
        <Button v-if="auth.can('user:write')" label="New user" icon="pi pi-plus" @click="openCreate" />
      </div>
    </div>

    <LoadError :error="error" what="users" @retry="refresh()" />
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
      <template #empty>No users found.</template>
      <Column field="email" header="E-mail" sortable />
      <Column header="Name">
        <template #body="{ data: u }">{{ [u.firstName, u.lastName].filter(Boolean).join(' ') || '—' }}</template>
      </Column>
      <Column field="status" header="Status" sortable>
        <template #body="{ data: u }"><Tag :value="u.status" :severity="statusSeverity(u.status)" /></template>
      </Column>
      <Column header="Roles">
        <template #body="{ data: u }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Tag v-for="r in u.roles" :key="r" :value="r" severity="secondary" /></div>
        </template>
      </Column>
      <Column field="lastLoginAt" header="Last login" sortable>
        <template #body="{ data: u }">{{ u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—' }}</template>
      </Column>
      <Column v-if="auth.can('user:write')" header="" style="width: 13rem; text-align: right">
        <template #body="{ data: u }">
          <Button
v-if="canImpersonate && u.status === 'ACTIVE'" v-tooltip.top="'Sign in as this user'" icon="pi pi-user-edit"
                  text rounded aria-label="Sign in as this user" @click="impersonate(u)" />
          <Button
v-if="u.status === 'PENDING'" v-tooltip.top="'Resend invitation'" icon="pi pi-send" text rounded
                  aria-label="Resend invitation" @click="resendInvite(u)" />
          <Button icon="pi pi-pencil" text rounded aria-label="Edit" @click="openEdit(u)" />
          <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Delete" @click="remove(u)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="form.id ? 'Edit user' : 'New user'" modal :style="{ width: '32rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="u-email">E-mail</label>
          <InputText id="u-email" v-model="form.email" type="email" :disabled="!!form.id" :invalid="!!errors.email" required fluid />
          <small v-if="errors.email" class="error">{{ errors.email }}</small>
        </div>
        <div class="grid-2">
          <div class="field">
            <label for="u-fn">First name</label>
            <InputText id="u-fn" v-model="form.firstName" fluid />
          </div>
          <div class="field">
            <label for="u-ln">Last name</label>
            <InputText id="u-ln" v-model="form.lastName" fluid />
          </div>
        </div>
        <div v-if="!form.id" class="field">
          <label for="u-pw">Initial password <span class="muted">(optional — leave empty to e-mail an invitation)</span></label>
          <Password id="u-pw" v-model="form.password" toggle-mask :invalid="!!errors.password" fluid />
          <small v-if="errors.password" class="error">{{ errors.password }}</small>
        </div>
        <div v-else class="field">
          <label for="u-status">Status</label>
          <Select id="u-status" v-model="form.status" :options="statuses" fluid />
        </div>
        <div class="field">
          <label for="u-roles">Roles</label>
          <MultiSelect id="u-roles" v-model="form.roles" :options="roles ?? []" option-label="name" option-value="name" display="chip" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="dialog = false" />
          <Button type="submit" label="Save" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
