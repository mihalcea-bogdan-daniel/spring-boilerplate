<script setup lang="ts">
import type { AccessScope, PageResponse, ProjectDto, SubjectDto } from '~/types/api'

definePageMeta({ permission: 'project:read' })

const { t, d } = useI18n()
const auth = useAuthStore()
const confirm = useConfirm()
const { request, pending: saving, fieldErrors } = useRequest()

const page = ref(0)
const size = ref(20)
const search = ref('')
const q = refDebounced(search, 300)
const scope = ref<AccessScope>('VISIBLE')
const scopes = computed(() => [
  { label: t('projects.scopeVisible'), value: 'VISIBLE' },
  { label: t('projects.scopeMine'), value: 'MINE' },
  { label: t('projects.scopeTeam'), value: 'TEAM' },
  ...(auth.can('project:read-all') ? [{ label: t('projects.scopeAll'), value: 'ALL' }] : []),
])

const { data, pending, error, refresh } = useApi<PageResponse<ProjectDto>>('/projects', {
  query: { page, size, q, scope, sort: 'createdAt,desc' }, server: false,
})

const ownerIcon = (o: SubjectDto) => ({ USER: 'pi pi-user', UNIT: 'pi pi-sitemap', GROUP: 'pi pi-users' }[o.kind])

// ---------------------------------------------------------------- create
const createDialog = ref(false)
const form = reactive<{ name: string, description: string, owner: SubjectDto | null }>({ name: '', description: '', owner: null })
function openCreate() {
  Object.assign(form, { name: '', description: '', owner: null })
  createDialog.value = true
}
async function create() {
  const res = await request('/projects', {
    method: 'POST',
    body: { name: form.name, description: form.description || null, ownerKind: form.owner?.kind, ownerId: form.owner?.id },
    success: t('projects.created'), error: t('projects.createFailed'),
  })
  if (!res.ok) return
  createDialog.value = false
  await refresh()
}

// ---------------------------------------------------------------- detail: edit + access
const detail = ref<ProjectDto | null>(null)
const detailOpen = ref(false)
const edit = reactive({ name: '', description: '' })
const { can, refresh: refreshCan } = useAccess('project', () => detail.value?.id)

function openDetail(p: ProjectDto) {
  detail.value = p
  Object.assign(edit, { name: p.name, description: p.description ?? '' })
  detailOpen.value = true
}

async function saveDetail() {
  const p = detail.value
  if (!p) return
  const res = await request<ProjectDto>(`/projects/${p.id}`, {
    method: 'PUT', body: { name: edit.name, description: edit.description || null },
    success: t('projects.saved'), error: t('projects.saveFailed'),
  })
  if (!res.ok) return
  detail.value = res.data
  await refresh()
}

function remove() {
  const p = detail.value
  if (!p) return
  confirm.require({
    header: t('projects.deleteHeader'),
    message: t('projects.deleteConfirm', { name: p.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/projects/${p.id}`, { method: 'DELETE', success: t('projects.deleted') })
      if (!res.ok) return
      detailOpen.value = false
      await refresh()
    },
  })
}

async function accessChanged() {
  await Promise.all([refresh(), refreshCan()])
}
</script>

<template>
  <div class="stack">
    <div class="row-between" style="flex-wrap: wrap">
      <h1 class="page-title">{{ t('projects.title') }}</h1>
      <div class="row" style="flex-wrap: wrap">
        <SelectButton v-model="scope" :options="scopes" option-label="label" option-value="value" :allow-empty="false" />
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="search" :placeholder="t('projects.search')" />
        </IconField>
        <Button v-if="auth.can('project:write')" :label="t('projects.new')" icon="pi pi-plus" @click="openCreate" />
      </div>
    </div>

    <LoadError :error="error" :what="t('projects.what')" @retry="refresh()" />
    <DataTable
      v-if="!error" :value="data?.content ?? []" :loading="pending" lazy paginator :rows="size"
      :total-records="data?.totalElements ?? 0" data-key="id" selection-mode="single"
      @page="e => { page = e.page; size = e.rows }" @row-click="e => openDetail(e.data)"
    >
      <template #empty>{{ t('projects.empty') }}</template>
      <Column field="name" :header="t('common.name')" />
      <Column :header="t('projects.owner')">
        <template #body="{ data: p }">
          <span v-for="o in p.owners" :key="o.id" class="row" style="gap: .4rem"><i :class="ownerIcon(o)" />{{ o.name }}</span>
        </template>
      </Column>
      <Column :header="t('projects.updated')">
        <template #body="{ data: p }">{{ d(p.updatedAt, 'datetime') }}</template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="createDialog" :header="t('projects.new')" modal :style="{ width: '30rem' }">
      <form class="stack" @submit.prevent="create">
        <div class="field">
          <label for="p-name">{{ t('common.name') }}</label>
          <InputText id="p-name" v-model="form.name" :invalid="!!fieldErrors.name" required fluid />
          <small v-if="fieldErrors.name" class="error">{{ fieldErrors.name }}</small>
        </div>
        <div class="field">
          <label for="p-desc">{{ t('common.description') }}</label>
          <Textarea id="p-desc" v-model="form.description" rows="3" auto-resize fluid />
        </div>
        <div class="field">
          <label>{{ t('projects.owner') }} <span class="muted">{{ t('projects.ownerHint') }}</span></label>
          <SubjectPicker v-model="form.owner" :kinds="['UNIT', 'GROUP']" :placeholder="t('projects.ownerMe')" />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="createDialog = false" />
          <Button type="submit" :label="t('common.create')" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>

    <Drawer v-model:visible="detailOpen" position="right" :header="detail?.name" style="width: min(34rem, 100vw)">
      <div v-if="detail" class="stack">
        <form class="stack" @submit.prevent="saveDetail">
          <div class="field">
            <label for="d-name">{{ t('common.name') }}</label>
            <InputText id="d-name" v-model="edit.name" :disabled="!can.write" required fluid />
          </div>
          <div class="field">
            <label for="d-desc">{{ t('common.description') }}</label>
            <Textarea id="d-desc" v-model="edit.description" :disabled="!can.write" rows="4" auto-resize fluid />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button v-if="can.delete" type="button" :label="t('common.delete')" icon="pi pi-trash" severity="danger" text @click="remove" />
            <Button v-if="can.write" type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" />
          </div>
        </form>
        <Divider align="left"><span class="muted">{{ t('access.title') }}</span></Divider>
        <AccessPanel :id="detail.id" type="project" @changed="accessChanged" />
      </div>
    </Drawer>
  </div>
</template>
