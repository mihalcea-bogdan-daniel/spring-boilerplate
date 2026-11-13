<script setup lang="ts">
import type { DataTablePageEvent } from 'primevue/datatable'
import type { PageResponse, TenantDto, TenantStatsDto, TenantStatus } from '~/types/api'

definePageMeta({ superAdminOnly: true })

const tenantStore = useTenantStore()
const { t, d } = useI18n()
const { request } = useRequest()
// separate instances so each dialog has its own spinner and field errors
const { request: provisionRequest, pending: creating, fieldErrors: createErrors } = useRequest()
const { request: deleteRequest, pending: deleting } = useRequest()
const { request: migrateRequest, pending: migrating } = useRequest()

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
const statuses = computed(() => (['ACTIVE', 'SUSPENDED'] as TenantStatus[]).map(s => ({ label: t(`status.${s}`), value: s })))

// --------------------------------------------------- provision
const createDialog = ref(false)
const createForm = reactive({ slug: '', name: '', domain: '', adminEmail: '', adminFirstName: '', adminLastName: '' })

async function provision() {
  const res = await provisionRequest<TenantDto>('/platform/tenants', {
    method: 'POST',
    body: { ...createForm, domain: createForm.domain || null },
    success: x => t('tenants.provisioned', { slug: x.slug, schema: x.schemaName }),
    error: t('tenants.provisionFailed'),
  })
  if (!res.ok) return
  createDialog.value = false
  Object.assign(createForm, { slug: '', name: '', domain: '', adminEmail: '', adminFirstName: '', adminLastName: '' })
  await refresh()
}

// --------------------------------------------------- edit
const editDialog = ref(false)
const { data: planNames } = useApi<string[]>('/platform/tenants/plans', { server: false })
const plans = computed(() => planNames.value ?? [])
const editForm = reactive<{ id: string, name: string, domain: string, status: TenantStatus, plan: string }>({ id: '', name: '', domain: '', status: 'ACTIVE', plan: 'free' })
function openEdit(tenant: TenantDto) {
  Object.assign(editForm, { id: tenant.id, name: tenant.name, domain: tenant.domain ?? '', status: tenant.status, plan: tenant.plan })
  editDialog.value = true
}
async function saveEdit() {
  const res = await request(`/platform/tenants/${editForm.id}`, {
    method: 'PUT',
    body: { name: editForm.name, domain: editForm.domain || null, status: editForm.status, plan: editForm.plan },
    success: t('tenants.saved'),
    error: t('tenants.saveFailed'),
  })
  if (!res.ok) return
  editDialog.value = false
  await refresh()
}

// --------------------------------------------------- status (schema version, size, usage)
const statsDialog = ref(false)
const stats = ref<TenantStatsDto | null>(null)
const statsTenant = ref<TenantDto | null>(null)
async function loadStats(tenant: TenantDto) {
  const res = await request<TenantStatsDto>(`/platform/tenants/${tenant.id}/stats`, { error: t('tenants.statsFailed') })
  if (res.ok) stats.value = res.data
  else statsDialog.value = false
}
async function openStats(tenant: TenantDto) {
  statsTenant.value = tenant
  stats.value = null
  statsDialog.value = true
  await loadStats(tenant)
}
async function migrateNow() {
  const target = statsTenant.value
  if (!target) return
  const res = await migrateRequest(`/platform/tenants/${target.id}/migrate`, { method: 'POST', success: t('tenants.migrated') })
  if (res.ok) await loadStats(target)
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
const deleteTarget = ref<TenantDto | null>(null)
const deleteConfirm = ref('')
function openDelete(tenant: TenantDto) {
  deleteTarget.value = tenant
  deleteConfirm.value = ''
  deleteDialog.value = true
}
async function deleteTenant() {
  const target = deleteTarget.value
  if (!target) return
  const res = await deleteRequest(`/platform/tenants/${target.id}`, {
    method: 'DELETE', query: { confirm: deleteConfirm.value }, success: t('tenants.deleted', { slug: target.slug }),
  })
  if (!res.ok) return
  if (tenantStore.managedTenant === target.slug) tenantStore.manage(null)
  deleteDialog.value = false
  await refresh()
}

/** Switch the dashboard into this tenant: subsequent /admin/* calls carry X-Tenant-ID. */
async function manage(tenant: TenantDto) {
  tenantStore.manage(tenant.slug)
  await navigateTo('/admin/users')
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('tenants.title') }}</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('tenants.search')" />
        </IconField>
        <Button :label="t('tenants.provision')" icon="pi pi-plus" @click="createDialog = true" />
      </div>
    </div>

    <LoadError :error="error" :what="t('tenants.what')" @retry="refresh()" />
    <DataTable
v-if="!error"
:value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
               :total-records="data?.totalElements ?? 0" :rows-per-page-options="[10, 20, 50]" data-key="id" @page="onPage">
      <template #empty>{{ t('tenants.empty') }}</template>
      <Column field="name" :header="t('common.name')" />
      <Column field="slug" :header="t('tenants.slug')"><template #body="{ data: row }"><code>{{ row.slug }}</code></template></Column>
      <Column field="domain" :header="t('tenants.domain')"><template #body="{ data: row }">{{ row.domain ?? '—' }}</template></Column>
      <Column field="schemaName" :header="t('tenants.schema')"><template #body="{ data: row }"><code>{{ row.schemaName }}</code></template></Column>
      <Column field="plan" :header="t('tenants.plan')"><template #body="{ data: row }"><Tag :value="row.plan" severity="secondary" /></template></Column>
      <Column field="status" :header="t('common.status')"><template #body="{ data: row }"><Tag :value="t(`status.${row.status}`)" :severity="severity(row.status)" /></template></Column>
      <Column style="width: 17rem; text-align: right">
        <template #body="{ data: row }">
          <Button v-tooltip.top="t('tenants.manageTip')" icon="pi pi-sign-in" text rounded :aria-label="t('tenants.manage')" @click="manage(row)" />
          <Button v-tooltip.top="t('common.status')" icon="pi pi-chart-bar" text rounded :aria-label="t('common.status')" @click="openStats(row)" />
          <Button icon="pi pi-pencil" text rounded :aria-label="t('common.edit')" @click="openEdit(row)" />
          <Button
v-if="row.status === 'SUSPENDED'" v-tooltip.top="t('tenants.deleteTip')" icon="pi pi-trash" text rounded
                  severity="danger" :aria-label="t('common.delete')" @click="openDelete(row)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="createDialog" :header="t('tenants.provision')" modal :style="{ width: '34rem' }">
      <form class="stack" @submit.prevent="provision">
        <div class="grid-2">
          <div class="field">
            <label for="t-slug">{{ t('tenants.slugLabel') }}</label>
            <InputText id="t-slug" v-model="createForm.slug" placeholder="acme" :invalid="!!createErrors.slug" required fluid />
            <small v-if="createErrors.slug" class="error">{{ createErrors.slug }}</small>
          </div>
          <div class="field">
            <label for="t-name">{{ t('common.name') }}</label>
            <InputText id="t-name" v-model="createForm.name" placeholder="Acme Inc." required fluid />
          </div>
        </div>
        <div class="field">
          <label for="t-domain">{{ t('tenants.domain') }} <span class="muted">{{ t('common.optional') }}</span></label>
          <InputText id="t-domain" v-model="createForm.domain" placeholder="portal.acme.com" fluid />
        </div>
        <Divider align="left"><span class="muted">{{ t('tenants.firstAdmin') }}</span></Divider>
        <div class="field">
          <label for="t-email">{{ t('common.email') }}</label>
          <InputText id="t-email" v-model="createForm.adminEmail" type="email" :invalid="!!createErrors.adminEmail" required fluid />
        </div>
        <div class="grid-2">
          <InputText v-model="createForm.adminFirstName" :placeholder="t('common.firstName')" fluid />
          <InputText v-model="createForm.adminLastName" :placeholder="t('common.lastName')" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="createDialog = false" />
          <Button type="submit" :label="t('tenants.provisionButton')" icon="pi pi-server" :loading="creating" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="statsDialog" :header="t('tenants.statusTitle', { name: statsTenant?.name ?? '' })" modal :style="{ width: '28rem' }">
      <div v-if="!stats" class="row" style="justify-content: center"><ProgressSpinner style="width: 2.5rem; height: 2.5rem" /></div>
      <div v-else class="stack">
        <div class="row-between">
          <span>{{ t('tenants.schemaVersion') }}</span>
          <span class="row">
            <code>{{ stats.schemaVersion ?? '—' }}</code>
            <Tag :value="stats.upToDate ? t('tenants.upToDate') : t('tenants.behind', { version: stats.latestVersion })" :severity="stats.upToDate ? 'success' : 'warn'" />
          </span>
        </div>
        <div class="row-between"><span>{{ t('tenants.lastMigrated') }}</span><span>{{ stats.lastMigratedAt ? d(stats.lastMigratedAt, 'datetime') : '—' }}</span></div>
        <div class="row-between"><span>{{ t('tenants.dataSize') }}</span><span>{{ formatBytes(stats.sizeBytes) }}</span></div>
        <div class="row-between"><span>{{ t('layout.users') }}</span><span>{{ stats.users }}</span></div>
        <div class="row-between"><span>{{ t('tenants.activeSessions') }}</span><span>{{ stats.activeSessions }}</span></div>
        <div v-if="!stats.upToDate" class="row" style="justify-content: flex-end">
          <Button :label="t('tenants.runMigrations')" icon="pi pi-database" :loading="migrating" @click="migrateNow" />
        </div>
      </div>
    </Dialog>

    <Dialog v-model:visible="deleteDialog" :header="t('tenants.deleteHeader')" modal :style="{ width: '30rem' }">
      <form class="stack" @submit.prevent="deleteTenant">
        <Message severity="error" :closable="false">
          <i18n-t keypath="tenants.deleteWarning">
            <template #schema><code>{{ deleteTarget?.schemaName }}</code></template>
          </i18n-t>
        </Message>
        <div class="field">
          <i18n-t keypath="tenants.typeToConfirm" tag="label" for="d-confirm">
            <template #slug><code>{{ deleteTarget?.slug }}</code></template>
          </i18n-t>
          <InputText id="d-confirm" v-model="deleteConfirm" autocomplete="off" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="deleteDialog = false" />
          <Button
type="submit" :label="t('tenants.deletePermanently')" severity="danger" icon="pi pi-trash" :loading="deleting"
                  :disabled="deleteConfirm !== deleteTarget?.slug" />
        </div>
      </form>
    </Dialog>

    <Dialog v-model:visible="editDialog" :header="t('tenants.edit')" modal :style="{ width: '30rem' }">
      <form class="stack" @submit.prevent="saveEdit">
        <div class="field"><label for="e-name">{{ t('common.name') }}</label><InputText id="e-name" v-model="editForm.name" required fluid /></div>
        <div class="field"><label for="e-domain">{{ t('tenants.domain') }}</label><InputText id="e-domain" v-model="editForm.domain" fluid /></div>
        <div class="field"><label for="e-status">{{ t('common.status') }}</label><Select id="e-status" v-model="editForm.status" :options="statuses" option-label="label" option-value="value" fluid /></div>
        <div class="field"><label for="e-plan">{{ t('tenants.plan') }}</label><Select id="e-plan" v-model="editForm.plan" :options="plans" fluid /></div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="editDialog = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
