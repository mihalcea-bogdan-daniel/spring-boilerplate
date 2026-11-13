<script setup lang="ts">
import type { GroupDto, SubjectDto } from '~/types/api'

// every signed-in person may create groups (a household, a committee) and see the ones they belong to
definePageMeta({ permission: 'org:read' })

const { t, te } = useI18n()
const kindLabel = (k: string) => (te(`groups.kinds.${k}`) ? t(`groups.kinds.${k}`) : k)
const confirm = useConfirm()
const { request, pending: saving, fieldErrors } = useRequest()

const { data: groups, pending, error, refresh } = useApi<GroupDto[]>('/groups', { server: false })

const dialog = ref(false)
const form = reactive<{ id?: string, name: string, kind: string }>({ name: '', kind: 'team' })
const kinds = computed(() => ['team', 'household', 'committee'].map(k => ({ label: t(`groups.kinds.${k}`), value: k })))

function open(g?: GroupDto) {
  Object.assign(form, g ? { id: g.id, name: g.name, kind: g.kind } : { id: undefined, name: '', kind: 'team' })
  dialog.value = true
}

async function save() {
  const res = await request(form.id ? `/groups/${form.id}` : '/groups', {
    method: form.id ? 'PUT' : 'POST', body: { name: form.name, kind: form.kind },
    success: t('groups.saved'), error: t('groups.saveFailed'),
  })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

function remove(g: GroupDto) {
  confirm.require({
    header: t('groups.deleteHeader'),
    message: t('groups.deleteConfirm', { name: g.name }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/groups/${g.id}`, { method: 'DELETE', success: t('groups.deleted', { name: g.name }) })
      if (res.ok) await refresh()
    },
  })
}

const newMember = ref<Record<string, SubjectDto | null>>({})
async function addMember(g: GroupDto) {
  const person = newMember.value[g.id]
  if (!person) return
  const res = await request(`/groups/${g.id}/members`, {
    method: 'PUT', body: { userId: person.id, role: 'member' }, success: t('groups.memberAdded', { name: person.name }),
  })
  if (!res.ok) return
  newMember.value[g.id] = null
  await refresh()
}

async function setRole(g: GroupDto, userId: string, role: 'admin' | 'member') {
  const res = await request(`/groups/${g.id}/members`, { method: 'PUT', body: { userId, role }, success: false })
  if (res.ok) await refresh()
}

async function removeMember(g: GroupDto, userId: string) {
  const res = await request(`/groups/${g.id}/members/${userId}`, { method: 'DELETE', success: t('groups.memberRemoved') })
  if (res.ok) await refresh()
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('groups.title') }}</h1>
      <Button :label="t('groups.new')" icon="pi pi-plus" @click="open()" />
    </div>
    <p class="muted">{{ t('groups.intro') }}</p>

    <LoadError :error="error" :what="t('groups.what')" @retry="refresh()" />
    <ProgressSpinner v-if="pending && !groups" style="width: 2.5rem; height: 2.5rem" />
    <p v-if="!error && groups && !groups.length" class="muted">{{ t('groups.empty') }}</p>
    <div v-if="!error" class="grid-2">
      <Card v-for="g in groups ?? []" :key="g.id">
        <template #title>
          <div class="row-between">
            <span class="row" style="gap: .5rem">{{ g.name }} <Tag :value="kindLabel(g.kind)" severity="secondary" /></span>
            <span v-if="g.canManage">
              <Button icon="pi pi-pencil" text rounded :aria-label="t('common.edit')" @click="open(g)" />
              <Button icon="pi pi-trash" text rounded severity="danger" :aria-label="t('common.delete')" @click="remove(g)" />
            </span>
          </div>
        </template>
        <template #content>
          <div class="stack">
            <div v-for="m in g.members" :key="m.userId" class="row-between">
              <span>{{ m.name }} <small class="muted">{{ m.email }}</small></span>
              <span class="row" style="gap: .25rem">
                <Select
                  v-if="g.canManage" :model-value="m.role" size="small"
                  :options="[{ label: t('groups.roles.admin'), value: 'admin' }, { label: t('groups.roles.member'), value: 'member' }]"
                  option-label="label" option-value="value" @update:model-value="(r: 'admin' | 'member') => setRole(g, m.userId, r)"
                />
                <Tag v-else :value="t(`groups.roles.${m.role}`)" severity="secondary" />
                <Button v-if="g.canManage" icon="pi pi-times" text rounded severity="danger" :aria-label="t('groups.removeMember')" @click="removeMember(g, m.userId)" />
              </span>
            </div>
            <div v-if="g.canManage" class="row">
              <div style="flex: 1"><SubjectPicker v-model="newMember[g.id]" :kinds="['USER']" :placeholder="t('org.findPerson')" /></div>
              <Button icon="pi pi-user-plus" :aria-label="t('groups.addMember')" :disabled="!newMember[g.id]" @click="addMember(g)" />
            </div>
          </div>
        </template>
      </Card>
    </div>

    <Dialog v-model:visible="dialog" :header="form.id ? t('groups.edit') : t('groups.new')" modal :style="{ width: '26rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="field">
          <label for="g-name">{{ t('common.name') }}</label>
          <InputText id="g-name" v-model="form.name" :invalid="!!fieldErrors.name" required fluid />
        </div>
        <div class="field">
          <label for="g-kind">{{ t('groups.kind') }}</label>
          <Select id="g-kind" v-model="form.kind" :options="kinds" option-label="label" option-value="value" fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="dialog = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
