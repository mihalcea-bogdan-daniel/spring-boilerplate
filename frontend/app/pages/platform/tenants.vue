<script setup lang="ts">
import type { DataTablePageEvent } from 'primevue/datatable'
import type { PageResponse, TenantDto, TenantStatsDto, TenantStatus } from '~/types/api'

definePageMeta({ superAdminOnly: true })

const toast = useToast()
const tenantStore = useTenantStore()
const { $api } = useNuxtApp()

const page = ref(0)
const size = ref(20)
const search = ref('')
const q = refDebounced(search, 300)
const { data, pending, error, refresh } = useApi<PageResponse<TenantDto>>('/platform/tenants', {
  query: { page, size, q, sort: 'createdAt,desc' },
  server: false,
})
function onPage(e: DataTablePageEvent) {
  page.value = e.page
  size.value = e.rows
}

const severity = (s: TenantStatus) => ({ ACTIVE: 'success', PROVISIONING: 'info', SUSPENDED: 'danger' }[s])
const statuses: TenantStatus[] = ['ACTIVE', 'SUSPENDED']

// --------------------------------------------------- provision
const createDialog = ref(false)
const creating = ref(false)
const createForm = reactive({ slug: '', name: '', domain: '', adminEmail: '', adminFirstName: '', adminLastName: '' })
const createErrors = ref<Record<string, string>>({})

async function provision() {
  creating.value = true
  createErrors.value = {}
  try {
    const t = await $api<TenantDto>('/platform/tenants', { method: 'POST', body: { ...createForm, domain: createForm.domain || null } })
    toast.add({ severity: 'success', summary: `Tenant ${t.slug} provisioned`, detail: `Schema ${t.schemaName}`, life: 4000 })
    createDialog.value = false
    Object.assign(createForm, { slug: '', name: '', domain: '', adminEmail: '', adminFirstName: '', adminLastName: '' })
    await refresh()
  } catch (e) {
    const p = problemOf(e)
    createErrors.value = p.errors ?? {}
    toast.add({ severity: 'error', summary: p.detail, life: 5000 })
  } finally {
    creating.value = false
  }
}

// --------------------------------------------------- edit
const editDialog = ref(false)
const { data: planNames } = useApi<string[]>('/platform/tenants/plans', { server: false })
const plans = computed(() => planNames.value ?? [])
const editForm = reactive<{ id: string, name: string, domain: string, status: TenantStatus, plan: string }>({ id: '', name: '', domain: '', status: 'ACTIVE', plan: 'free' })
function openEdit(t: TenantDto) {
  Object.assign(editForm, { id: t.id, name: t.name, domain: t.domain ?? '', status: t.status, plan: t.plan })
  editDialog.value = true
}
async function saveEdit() {
  try {
    await $api(`/platform/tenants/${editForm.id}`, { method: 'PUT', body: { name: editForm.name, domain: editForm.domain || null, status: editForm.status, plan: editForm.plan } })
    editDialog.value = false
    await refresh()
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  }
}

// --------------------------------------------------- status (schema version, size, usage)
const statsDialog = ref(false)
const stats = ref<TenantStatsDto | null>(null)
const statsTenant = ref<TenantDto | null>(null)
const migrating = ref(false)
async function openStats(t: TenantDto) {
  statsTenant.value = t
  stats.value = null
  statsDialog.value = true
  try {
    stats.value = await $api<TenantStatsDto>(`/platform/tenants/${t.id}/stats`)
  } catch (e) {
    statsDialog.value = false
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  }
}
async function migrateNow() {
  if (!statsTenant.value) return
  migrating.value = true
  try {
    await $api(`/platform/tenants/${statsTenant.value.id}/migrate`, { method: 'POST' })
    stats.value = await $api<TenantStatsDto>(`/platform/tenants/${statsTenant.value.id}/stats`)
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  } finally {
    migrating.value = false
  }
}
function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) { value /= 1024; i++ }
  return `${value.toFixed(1)} ${units[i]}`
}

// --------------------------------------------------- offboarding (only suspended tenants)
const deleteDialog = ref(false)
const deleting = ref(false)
const deleteTarget = ref<TenantDto | null>(null)
const deleteConfirm = ref('')
function openDelete(t: TenantDto) {
  deleteTarget.value = t
  deleteConfirm.value = ''
  deleteDialog.value = true
}
async function deleteTenant() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await $api(`/platform/tenants/${deleteTarget.value.id}`, { method: 'DELETE', query: { confirm: deleteConfirm.value } })
    if (tenantStore.managedTenant === deleteTarget.value.slug) tenantStore.manage(null)
    toast.add({ severity: 'success', summary: `Tenant ${deleteTarget.value.slug} deleted`, life: 4000 })
    deleteDialog.value = false
    await refresh()
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  } finally {
    deleting.value = false
  }
}

/** Switch the dashboard into this tenant: subsequent /admin/* calls carry X-Tenant-ID. */
async function manage(t: TenantDto) {
  tenantStore.manage(t.slug)
  await navigateTo('/admin/users')
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">Tenants</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" placeholder="Search tenants" />
        </IconField>
        <Button label="Provision tenant" icon="pi pi-plus" @click="createDialog = true" />
      </div>
    </div>

    <LoadError :error="error" what="tenants" @retry="refresh()" />
    <DataTable
v-if="!error"
:value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
               :total-records="data?.totalElements ?? 0" :rows-per-page-options="[10, 20, 50]" data-key="id" @page="onPage">
      <template #empty>No tenants.</template>
      <Column field="name" header="Name" />
      <Column field="slug" header="Slug"><template #body="{ data: t }"><code>{{ t.slug }}</code></template></Column>
      <Column field="domain" header="Custom domain"><template #body="{ data: t }">{{ t.domain ?? '—' }}</template></Column>
      <Column field="schemaName" header="Schema"><template #body="{ data: t }"><code>{{ t.schemaName }}</code></template></Column>
      <Column field="plan" header="Plan"><template #body="{ data: t }"><Tag :value="t.plan" severity="secondary" /></template></Column>
      <Column field="status" header="Status"><template #body="{ data: t }"><Tag :value="t.status" :severity="severity(t.status)" /></template></Column>
      <Column style="width: 17rem; text-align: right">
        <template #body="{ data: t }">
          <Button v-tooltip.top="'Manage users/settings (X-Tenant-ID)'" icon="pi pi-sign-in" text rounded aria-label="Manage" @click="manage(t)" />
          <Button v-tooltip.top="'Status'" icon="pi pi-chart-bar" text rounded aria-label="Status" @click="openStats(t)" />
          <Button icon="pi pi-pencil" text rounded aria-label="Edit" @click="openEdit(t)" />
          <Button
v-if="t.status === 'SUSPENDED'" v-tooltip.top="'Delete tenant and all its data'" icon="pi pi-trash" text rounded
                  severity="danger" aria-label="Delete" @click="openDelete(t)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="createDialog" header="Provision tenant" modal :style="{ width: '34rem' }">
      <form class="stack" @submit.prevent="provision">
        <div class="grid-2">
          <div class="field">
            <label for="t-slug">Slug (sub-domain)</label>
            <InputText id="t-slug" v-model="createForm.slug" placeholder="acme" :invalid="!!createErrors.slug" required fluid />
            <small v-if="createErrors.slug" class="error">{{ createErrors.slug }}</small>
          </div>
          <div class="field">
            <label for="t-name">Name</label>
            <InputText id="t-name" v-model="createForm.name" placeholder="Acme Inc." required fluid />
          </div>
        </div>
        <div class="field">
          <label for="t-domain">Custom domain <span class="muted">(optional)</span></label>
          <InputText id="t-domain" v-model="createForm.domain" placeholder="portal.acme.com" fluid />
        </div>
        <Divider align="left"><span class="muted">First administrator</span></Divider>
        <div class="field">
          <label for="t-email">E-mail</label>
          <InputText id="t-email" v-model="createForm.adminEmail" type="email" :invalid="!!createErrors.adminEmail" required fluid />
        </div>
        <div class="grid-2">
          <InputText v-model="createForm.adminFirstName" placeholder="First name" fluid />
          <InputText v-model="createForm.adminLastName" placeholder="Last name" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="createDialog = false" />
          <Button type="submit" label="Provision" icon="pi pi-server" :loading="creating" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="statsDialog" :header="`Status — ${statsTenant?.name ?? ''}`" modal :style="{ width: '28rem' }">
      <div v-if="!stats" class="row" style="justify-content: center"><ProgressSpinner style="width: 2.5rem; height: 2.5rem" /></div>
      <div v-else class="stack">
        <div class="row-between">
          <span>Schema version</span>
          <span class="row">
            <code>{{ stats.schemaVersion ?? '—' }}</code>
            <Tag :value="stats.upToDate ? 'up to date' : `behind (latest ${stats.latestVersion})`" :severity="stats.upToDate ? 'success' : 'warn'" />
          </span>
        </div>
        <div class="row-between"><span>Last migrated</span><span>{{ stats.lastMigratedAt ? new Date(stats.lastMigratedAt).toLocaleString() : '—' }}</span></div>
        <div class="row-between"><span>Data size</span><span>{{ formatBytes(stats.sizeBytes) }}</span></div>
        <div class="row-between"><span>Users</span><span>{{ stats.users }}</span></div>
        <div class="row-between"><span>Active sessions</span><span>{{ stats.activeSessions }}</span></div>
        <div v-if="!stats.upToDate" class="row" style="justify-content: flex-end">
          <Button label="Run migrations" icon="pi pi-database" :loading="migrating" @click="migrateNow" />
        </div>
      </div>
    </Dialog>

    <Dialog v-model:visible="deleteDialog" header="Delete tenant" modal :style="{ width: '30rem' }">
      <form class="stack" @submit.prevent="deleteTenant">
        <Message severity="error" :closable="false">
          This permanently drops schema <code>{{ deleteTarget?.schemaName }}</code> with every user, role and setting.
          It cannot be undone — take a database backup first.
        </Message>
        <div class="field">
          <label for="d-confirm">Type <code>{{ deleteTarget?.slug }}</code> to confirm</label>
          <InputText id="d-confirm" v-model="deleteConfirm" autocomplete="off" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="deleteDialog = false" />
          <Button
type="submit" label="Delete permanently" severity="danger" icon="pi pi-trash" :loading="deleting"
                  :disabled="deleteConfirm !== deleteTarget?.slug" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="editDialog" header="Edit tenant" modal :style="{ width: '30rem' }">
      <form class="stack" @submit.prevent="saveEdit">
        <div class="field"><label for="e-name">Name</label><InputText id="e-name" v-model="editForm.name" required fluid /></div>
        <div class="field"><label for="e-domain">Custom domain</label><InputText id="e-domain" v-model="editForm.domain" fluid /></div>
        <div class="field"><label for="e-status">Status</label><Select id="e-status" v-model="editForm.status" :options="statuses" fluid /></div>
        <div class="field"><label for="e-plan">Plan</label><Select id="e-plan" v-model="editForm.plan" :options="plans" fluid /></div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="editDialog = false" />
          <Button type="submit" label="Save" icon="pi pi-check" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
