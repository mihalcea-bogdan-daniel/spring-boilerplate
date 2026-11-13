<script setup lang="ts">
import type { ChartNode, OrgMemberDto, PersonDto, RankDto } from '~/types/api'

definePageMeta({ permission: 'org:read' })

interface TreeNode { key: string, data: ChartNode, children: TreeNode[] }
interface SearchHit { key: string, kind: 'unit' | 'person', label: string, detail: string, unitId: string }

const { t } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const { ranks, byId: rankById, label: rankLabel } = useRanks()
const { request, pending: saving } = useRequest()
const canManage = computed(() => auth.can('org:manage'))

const { data: chart, pending, error, refresh } = useApi<ChartNode>('/org/chart', { server: false })

// ---------------------------------------------------------------- chart model
const toTree = (n: ChartNode): TreeNode => ({ key: n.id, data: n, children: n.children.map(toTree) })
const tree = computed(() => (chart.value ? toTree(chart.value) : null))

const units = computed(() => {
  const all = new Map<string, ChartNode>()
  const walk = (n: ChartNode) => { all.set(n.id, n); n.children.forEach(walk) }
  if (chart.value) walk(chart.value)
  return all
})
const collapsedKeys = ref<Record<string, boolean>>({})
const selectionKeys = ref<Record<string, boolean>>({})

function countMembers(n: ChartNode): number {
  return n.members.length + n.children.reduce((sum, c) => sum + countMembers(c), 0)
}

// ---------------------------------------------------------------- search (people, units, rank names and synonyms)
const query = ref<SearchHit | string | null>(null)
const hits = ref<SearchHit[]>([])
const index = computed<SearchHit[]>(() => {
  const list: SearchHit[] = []
  for (const u of units.value.values()) {
    list.push({ key: `u-${u.id}`, kind: 'unit', label: u.name, detail: '', unitId: u.id })
    for (const m of u.members) {
      const rank = rankById.value.get(m.rankId)
      const words = [rankLabel(rank), ...(rank ? [...Object.values(rank.labels), ...rank.aliases] : [])]
      list.push({ key: `p-${u.id}-${m.userId}`, kind: 'person', label: m.name, unitId: u.id,
        detail: `${rankLabel(rank)} · ${u.name} · ${m.email} · ${words.join(' ')}` })
    }
  }
  return list
})

function search(e: { query: string }) {
  const q = e.query.trim().toLowerCase()
  hits.value = index.value.filter(h => `${h.label} ${h.detail}`.toLowerCase().includes(q)).slice(0, 20)
}

function reveal(hit: SearchHit) {
  // expand every unit above the hit, select it and open its panel
  const path = new Set<string>()
  let u = units.value.get(hit.unitId)
  while (u) {
    path.add(u.id)
    u = u.parentId ? units.value.get(u.parentId) : undefined
  }
  collapsedKeys.value = Object.fromEntries(Object.entries(collapsedKeys.value).filter(([k]) => !path.has(k)))
  open(hit.unitId)
  nextTick(() => document.getElementById(`unit-${hit.unitId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' }))
}

// ---------------------------------------------------------------- unit panel
const panel = ref(false)
const unitId = ref<string | null>(null)
const unit = computed(() => (unitId.value ? units.value.get(unitId.value) ?? null : null))
const canEdit = computed(() => canManage.value || unit.value?.leadLevel != null)

function open(id: string) {
  unitId.value = id
  selectionKeys.value = { [id]: true }
  newMember.value = null
  newRank.value = null
  rename.value = units.value.get(id)?.name ?? ''
  moveTo.value = units.value.get(id)?.parentId ?? null
  panel.value = true
}

/** Ranks the user may hand out here: all with org:manage, else strictly below their lead level. */
const assignable = computed<RankDto[]>(() => ranks.value.filter(r =>
  canManage.value || (unit.value?.leadLevel != null && r.level < unit.value.leadLevel)))
const rankOptions = computed(() => assignable.value.map(r => ({ label: `${rankLabel(r)} (${r.level})`, value: r.id })))

function canTouch(m: OrgMemberDto) {
  if (canManage.value) return true
  const lead = unit.value?.leadLevel
  return lead != null && m.level < lead && m.userId !== auth.user?.id
}

const newMember = ref<PersonDto | null>(null)
const newRank = ref<string | null>(null)
const people = ref<PersonDto[]>([])
const { $api } = useNuxtApp()
async function searchPeople(e: { query: string }) {
  try {
    people.value = await $api<PersonDto[]>('/org/people', { query: { q: e.query } })
  } catch {
    people.value = []
  }
}

async function addMember() {
  if (!unit.value || !newMember.value || !newRank.value) return
  const res = await request(`/org/units/${unit.value.id}/members`, {
    method: 'POST', body: { userId: newMember.value.id, rankId: newRank.value },
    success: t('org.memberAdded', { name: newMember.value.name }), error: t('org.memberAddFailed'),
  })
  if (!res.ok) return
  newMember.value = null
  await refresh()
}

async function changeRank(m: OrgMemberDto, rankId: string) {
  if (!unit.value) return
  const res = await request(`/org/units/${unit.value.id}/members/${m.userId}`, {
    method: 'PUT', body: { rankId }, success: t('org.rankChanged', { name: m.name }),
  })
  if (res.ok) await refresh()
}

function removeMember(m: OrgMemberDto) {
  const u = unit.value
  if (!u) return
  confirm.require({
    header: t('org.removeHeader'),
    message: t('org.removeConfirm', { name: m.name, unit: u.name }),
    acceptProps: { label: t('org.remove'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/org/units/${u.id}/members/${m.userId}`, {
        method: 'DELETE', success: t('org.memberRemoved', { name: m.name }),
      })
      if (res.ok) await refresh()
    },
  })
}

// ---------------------------------------------------------------- restructuring (org:manage)
const rename = ref('')
const moveTo = ref<string | null>(null)
const subUnit = ref('')
const moveOptions = computed(() => {
  const u = unit.value
  if (!u) return []
  const below = new Set<string>()
  const walk = (n: ChartNode) => { below.add(n.id); n.children.forEach(walk) }
  walk(u)
  return [...units.value.values()].filter(x => !below.has(x.id)).map(x => ({ label: x.name, value: x.id }))
})

async function saveUnit() {
  const u = unit.value
  if (!u) return
  const res = await request(`/admin/org/units/${u.id}`, {
    method: 'PUT', body: { name: rename.value, parentId: u.parentId ? moveTo.value : null, sortOrder: u.sortOrder },
    success: t('org.unitSaved'), error: t('org.unitSaveFailed'),
  })
  if (res.ok) await refresh()
}

async function addSubUnit() {
  const u = unit.value
  if (!u || !subUnit.value.trim()) return
  const res = await request<{ id: string }>('/admin/org/units', {
    method: 'POST', body: { name: subUnit.value, parentId: u.id, sortOrder: u.children.length + 1 },
    success: t('org.unitCreated', { name: subUnit.value }),
  })
  if (!res.ok) return
  subUnit.value = ''
  await refresh()
}

function deleteUnit() {
  const u = unit.value
  if (!u) return
  confirm.require({
    header: t('org.deleteUnitHeader'),
    message: t('org.deleteUnitConfirm', { name: u.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/org/units/${u.id}`, { method: 'DELETE', success: t('org.unitDeleted', { name: u.name }) })
      if (!res.ok) return
      panel.value = false
      await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between" style="flex-wrap: wrap">
      <h1 class="page-title">{{ t('org.title') }}</h1>
      <div class="row">
        <AutoComplete
          v-model="query" :suggestions="hits" option-label="label" :placeholder="t('org.search')"
          style="min-width: 18rem" @complete="search" @option-select="e => reveal(e.value)"
        >
          <template #option="{ option }">
            <span class="row" style="gap: .5rem">
              <i :class="option.kind === 'unit' ? 'pi pi-sitemap' : 'pi pi-user'" />
              <span>{{ option.label }}</span>
              <span v-if="option.kind === 'person'" class="muted">{{ option.detail.split(' · ').slice(0, 2).join(' · ') }}</span>
            </span>
          </template>
        </AutoComplete>
        <Button v-if="canManage" :label="t('org.ranks')" icon="pi pi-sort-amount-down" severity="secondary" outlined as="router-link" to="/admin/ranks" />
      </div>
    </div>
    <p class="muted">{{ t('org.intro') }}</p>

    <LoadError :error="error" :what="t('org.what')" @retry="refresh()" />
    <div v-if="!error" class="org-scroll">
      <ProgressSpinner v-if="pending && !tree" style="width: 2.5rem; height: 2.5rem" />
      <OrganizationChart
        v-if="tree" v-model:collapsed-keys="collapsedKeys" v-model:selection-keys="selectionKeys"
        :value="tree" collapsible selection-mode="single" @node-select="n => open(n.key as string)"
      >
        <template #default="{ node }">
          <div :id="`unit-${node.data.id}`" class="org-node">
            <strong>{{ node.data.name }}</strong>
            <template v-if="node.data.members[0]">
              <span class="row" style="gap: .4rem; justify-content: center">
                <Avatar :label="node.data.members[0].name.charAt(0).toUpperCase()" shape="circle" size="small" />
                <span>{{ node.data.members[0].name }}</span>
              </span>
              <RankTag :rank="node.data.members[0].rankId" />
            </template>
            <small class="muted">{{ t('org.people', countMembers(node.data)) }}</small>
            <i v-if="node.data.leadLevel != null" v-tooltip.bottom="t('org.youLead')" class="pi pi-star-fill org-lead" />
          </div>
        </template>
      </OrganizationChart>
    </div>

    <Drawer v-model:visible="panel" position="right" :header="unit?.name" style="width: min(32rem, 100vw)">
      <div v-if="unit" class="stack">
        <DataTable :value="unit.members" data-key="userId" size="small">
          <template #empty>{{ t('org.noMembers') }}</template>
          <Column :header="t('common.name')">
            <template #body="{ data: m }">
              <div>{{ m.name }}</div>
              <small class="muted">{{ m.email }}</small>
            </template>
          </Column>
          <Column :header="t('org.rank')">
            <template #body="{ data: m }">
              <Select
                v-if="canTouch(m) && rankOptions.length" :model-value="m.rankId" :options="rankOptions"
                option-label="label" option-value="value" size="small"
                @update:model-value="(v: string) => changeRank(m, v)"
              />
              <RankTag v-else :rank="m.rankId" />
            </template>
          </Column>
          <Column style="width: 3rem; text-align: right">
            <template #body="{ data: m }">
              <Button v-if="canTouch(m)" icon="pi pi-times" text rounded severity="danger" :aria-label="t('org.remove')" @click="removeMember(m)" />
            </template>
          </Column>
        </DataTable>

        <template v-if="canEdit">
          <Divider align="left"><span class="muted">{{ t('org.addMember') }}</span></Divider>
          <AutoComplete v-model="newMember" :suggestions="people" option-label="name" :placeholder="t('org.findPerson')" force-selection fluid @complete="searchPeople">
            <template #option="{ option }"><span>{{ option.name }} <span class="muted">{{ option.email }}</span></span></template>
          </AutoComplete>
          <div class="row">
            <Select v-model="newRank" :options="rankOptions" option-label="label" option-value="value" :placeholder="t('org.rank')" style="flex: 1" />
            <Button :label="t('org.add')" icon="pi pi-user-plus" :disabled="!newMember || !newRank" :loading="saving" @click="addMember" />
          </div>
          <small v-if="!canManage" class="muted">{{ t('org.leaderHint', { level: unit.leadLevel }) }}</small>
        </template>

        <template v-if="canManage">
          <Divider align="left"><span class="muted">{{ t('org.unitSettings') }}</span></Divider>
          <div class="field">
            <label for="u-name">{{ t('common.name') }}</label>
            <InputText id="u-name" v-model="rename" fluid />
          </div>
          <div v-if="unit.parentId" class="field">
            <label for="u-parent">{{ t('org.parent') }}</label>
            <Select id="u-parent" v-model="moveTo" :options="moveOptions" option-label="label" option-value="value" filter fluid />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button v-if="unit.parentId" :label="t('common.delete')" icon="pi pi-trash" severity="danger" text @click="deleteUnit" />
            <Button :label="t('common.save')" icon="pi pi-check" :loading="saving" @click="saveUnit" />
          </div>
          <div class="row">
            <InputText v-model="subUnit" :placeholder="t('org.subUnitName')" style="flex: 1" @keyup.enter="addSubUnit" />
            <Button :label="t('org.addSubUnit')" icon="pi pi-plus" severity="secondary" :disabled="!subUnit.trim()" @click="addSubUnit" />
          </div>
        </template>
      </div>
    </Drawer>
  </div>
</template>

<style scoped>
.org-scroll {
  overflow-x: auto;
  padding-bottom: 1rem;
}
.org-node {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: .35rem;
  min-width: 9rem;
}
.org-lead {
  position: absolute;
  top: -.4rem;
  right: -.4rem;
  color: var(--p-primary-color);
  font-size: .8rem;
}
</style>
