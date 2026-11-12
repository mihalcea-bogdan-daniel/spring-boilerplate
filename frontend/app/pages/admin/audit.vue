<script setup lang="ts">
import type { DataTablePageEvent } from 'primevue/datatable'
import type { AuditEventDto, PageResponse } from '~/types/api'

definePageMeta({ permission: 'audit:read' })

const page = ref(0)
const size = ref(25)
const search = ref('')
const q = refDebounced(search, 300)

const { data, pending, error, refresh } = useApi<PageResponse<AuditEventDto>>('/admin/audit', {
  query: { page, size, q, sort: 'occurredAt,desc' },
  server: false,
})
function onPage(e: DataTablePageEvent) {
  page.value = e.page
  size.value = e.rows
}

const severity = (action: string) =>
  action.endsWith('failed') || action.endsWith('deleted') ? 'danger' : action.startsWith('auth.') ? 'info' : 'secondary'
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">Audit log</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" placeholder="Action, actor or target" />
        </IconField>
        <Button icon="pi pi-refresh" text rounded aria-label="Refresh" @click="refresh()" />
      </div>
    </div>

    <LoadError :error="error" what="the audit log" @retry="refresh()" />
    <DataTable
      v-if="!error"
:value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
               :total-records="data?.totalElements ?? 0" :rows-per-page-options="[25, 50, 100]" data-key="id"
               size="small" @page="onPage">
      <template #empty>No events recorded.</template>
      <Column header="When" style="white-space: nowrap">
        <template #body="{ data: e }">{{ new Date(e.occurredAt).toLocaleString() }}</template>
      </Column>
      <Column header="Action">
        <template #body="{ data: e }"><Tag :value="e.action" :severity="severity(e.action)" /></template>
      </Column>
      <Column header="Actor">
        <template #body="{ data: e }">
          {{ e.actorEmail ?? '—' }}
          <Tag v-if="e.actorTenant && e.actorTenant === 'platform'" value="platform" severity="warn" />
        </template>
      </Column>
      <Column header="Target">
        <template #body="{ data: e }">
          <span v-if="e.targetType" class="muted">{{ e.targetType }}</span> <code v-if="e.targetId">{{ e.targetId }}</code>
        </template>
      </Column>
      <Column field="details" header="Details" />
      <Column field="ipAddress" header="IP" />
    </DataTable>
  </div>
</template>
