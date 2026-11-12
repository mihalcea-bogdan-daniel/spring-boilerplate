<script setup lang="ts">
import type { DataTablePageEvent } from 'primevue/datatable'
import type { AuditEventDto, PageResponse } from '~/types/api'

definePageMeta({ permission: 'audit:read' })

const { t, d } = useI18n()
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
      <h1 class="page-title">{{ t('audit.title') }}</h1>
      <div class="row">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('audit.search')" />
        </IconField>
        <Button icon="pi pi-refresh" text rounded :aria-label="t('common.refresh')" @click="refresh()" />
      </div>
    </div>

    <LoadError :error="error" :what="t('audit.what')" @retry="refresh()" />
    <DataTable
      v-if="!error"
:value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
               :total-records="data?.totalElements ?? 0" :rows-per-page-options="[25, 50, 100]" data-key="id"
               size="small" @page="onPage">
      <template #empty>{{ t('audit.empty') }}</template>
      <Column :header="t('audit.when')" style="white-space: nowrap">
        <template #body="{ data: e }">{{ d(e.occurredAt, 'datetime') }}</template>
      </Column>
      <Column :header="t('audit.action')">
        <template #body="{ data: e }"><Tag :value="e.action" :severity="severity(e.action)" /></template>
      </Column>
      <Column :header="t('audit.actor')">
        <template #body="{ data: e }">
          {{ e.actorEmail ?? '—' }}
          <Tag v-if="e.actorTenant && e.actorTenant === 'platform'" :value="t('layout.platform')" severity="warn" />
        </template>
      </Column>
      <Column :header="t('audit.target')">
        <template #body="{ data: e }">
          <span v-if="e.targetType" class="muted">{{ e.targetType }}</span> <code v-if="e.targetId">{{ e.targetId }}</code>
        </template>
      </Column>
      <Column field="details" :header="t('audit.details')" />
      <Column field="ipAddress" :header="t('audit.ip')" />
    </DataTable>
  </div>
</template>
