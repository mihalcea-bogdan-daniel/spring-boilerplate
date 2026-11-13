<script setup lang="ts">
import type { AuditEventDto, PageResponse, SubordinateDto } from '~/types/api'

// everyone may open it; it lists only the people you supervise (often nobody)
definePageMeta({ permission: 'org:read' })

const { t, d } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const { request } = useRequest()

const { data: team, pending, error, refresh } = useApi<SubordinateDto[]>('/me/team', { server: false })

const page = ref(0)
const size = ref(25)
const { data: activity, pending: activityPending, error: activityError, refresh: refreshActivity }
  = useApi<PageResponse<AuditEventDto>>('/me/team/activity', { query: { page, size }, server: false })

function signInAs(p: SubordinateDto) {
  confirm.require({
    header: t('team.signInAsHeader'),
    message: t('team.signInAsConfirm', { name: p.name }),
    acceptProps: { label: t('team.signInAs'), severity: 'warn' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request<{ url: string }>(`/me/team/${p.id}/impersonate`, { method: 'POST', success: false })
      if (res.ok) window.open(res.data.url, '_blank', 'noopener')
    },
  })
}
</script>

<template>
  <div class="stack">
    <h1 class="page-title">{{ t('team.title') }}</h1>
    <p class="muted">{{ t('team.intro') }}</p>

    <Tabs :value="0">
      <TabList>
        <Tab :value="0">{{ t('team.people') }}</Tab>
        <Tab :value="1">{{ t('team.activity') }}</Tab>
      </TabList>
      <TabPanels>
        <TabPanel :value="0">
          <LoadError :error="error" :what="t('team.what')" @retry="refresh()" />
          <DataTable v-if="!error" :value="team ?? []" :loading="pending" data-key="id">
            <template #empty>{{ t('team.empty') }}</template>
            <Column :header="t('common.name')">
              <template #body="{ data: p }">
                <div>{{ p.name }}</div>
                <small class="muted">{{ p.email }}</small>
              </template>
            </Column>
            <Column :header="t('team.placement')">
              <template #body="{ data: p }">
                <div v-for="pl in p.placements" :key="pl.unitId" class="row" style="gap: .4rem">
                  <span>{{ pl.unitName }}</span><RankTag :rank="pl.rankId" />
                </div>
              </template>
            </Column>
            <Column :header="t('common.status')">
              <template #body="{ data: p }"><Tag :value="t(`status.${p.status}`)" :severity="p.status === 'ACTIVE' ? 'success' : 'warn'" /></template>
            </Column>
            <Column :header="t('users.lastLogin')">
              <template #body="{ data: p }">{{ p.lastLoginAt ? d(p.lastLoginAt, 'datetime') : '—' }}</template>
            </Column>
            <Column v-if="auth.can('org:impersonate') && !auth.isImpersonating" style="width: 4rem; text-align: right">
              <template #body="{ data: p }">
                <Button
                  v-if="p.status === 'ACTIVE'" v-tooltip.top="t('team.signInAs')" icon="pi pi-user-edit"
                  text rounded :aria-label="t('team.signInAs')" @click="signInAs(p)"
                />
              </template>
            </Column>
          </DataTable>
        </TabPanel>
        <TabPanel :value="1">
          <LoadError :error="activityError" :what="t('team.activityWhat')" @retry="refreshActivity()" />
          <DataTable
            v-if="!activityError" :value="activity?.content ?? []" :loading="activityPending" lazy paginator
            :rows="size" :total-records="activity?.totalElements ?? 0" data-key="id" size="small"
            @page="e => { page = e.page; size = e.rows }"
          >
            <template #empty>{{ t('audit.empty') }}</template>
            <Column :header="t('audit.when')"><template #body="{ data: e }">{{ d(e.occurredAt, 'datetime') }}</template></Column>
            <Column :header="t('audit.actor')" field="actorEmail" />
            <Column :header="t('audit.action')"><template #body="{ data: e }"><Tag :value="e.action" severity="secondary" /></template></Column>
            <Column :header="t('audit.details')" field="details" />
          </DataTable>
        </TabPanel>
      </TabPanels>
    </Tabs>
  </div>
</template>
