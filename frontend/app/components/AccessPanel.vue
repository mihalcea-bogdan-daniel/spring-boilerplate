<script setup lang="ts">
/**
 * Who has access to one owned resource, and sharing controls when the user may share. Works for any resource
 * type registered in the backend's access model:   <AccessPanel type="project" :id="project.id" />
 */
import type { SubjectDto } from '~/types/api'

const props = defineProps<{ type: string, id: string }>()
const emit = defineEmits<{ changed: [] }>()
const { t, te } = useI18n()
const confirm = useConfirm()
const { access, can, error, refresh } = useAccess(props.type, () => props.id)
const { request, pending } = useRequest()

const subject = ref<SubjectDto | null>(null)
const relation = ref('viewer')
const relationOptions = computed(() => (access.value?.relationNames ?? [])
  .filter(r => r !== access.value?.ownerRelation)
  .map(r => ({ label: relationLabel(r), value: r })))

function relationLabel(r: string) {
  const key = `access.relations.${r}`
  return te(key) ? t(key) : r
}

const icon = (kind: SubjectDto['kind']) => ({ USER: 'pi pi-user', UNIT: 'pi pi-sitemap', GROUP: 'pi pi-users' }[kind])

async function after(ok: boolean) {
  if (!ok) return
  subject.value = null
  await refresh()
  emit('changed')
}

async function share() {
  if (!subject.value) return
  const res = await request(`/access/${props.type}/${props.id}/relations`, {
    method: 'POST',
    body: { relation: relation.value, subjectKind: subject.value.kind, subjectId: subject.value.id },
    success: t('access.shared', { name: subject.value.name }),
  })
  await after(res.ok)
}

async function revoke(rel: string, s: SubjectDto) {
  const res = await request(`/access/${props.type}/${props.id}/relations`, {
    method: 'DELETE',
    body: { relation: rel, subjectKind: s.kind, subjectId: s.id },
    success: t('access.revoked', { name: s.name }),
  })
  await after(res.ok)
}

function transfer() {
  const target = subject.value
  if (!target) return
  confirm.require({
    header: t('access.transferHeader'),
    message: t('access.transferConfirm', { name: target.name }),
    acceptProps: { label: t('access.transfer'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/access/${props.type}/${props.id}/transfer`, {
        method: 'POST', body: { subjectKind: target.kind, subjectId: target.id },
        success: t('access.transferred', { name: target.name }),
      })
      await after(res.ok)
    },
  })
}
</script>

<template>
  <div class="stack">
    <LoadError :error="error" :what="t('access.what')" @retry="refresh()" />
    <template v-if="access">
      <div v-for="r in access.relations" :key="`${r.relation}-${r.subject.kind}-${r.subject.id}`" class="row-between">
        <span class="row" style="gap: .5rem">
          <i :class="icon(r.subject.kind)" />
          <span>{{ r.subject.name }}</span>
          <span v-if="r.subject.detail" class="muted">{{ r.subject.detail }}</span>
        </span>
        <span class="row" style="gap: .25rem">
          <Tag :value="relationLabel(r.relation)" :severity="r.relation === access.ownerRelation ? 'contrast' : 'secondary'" />
          <Button
            v-if="can.share && r.relation !== access.ownerRelation"
            icon="pi pi-times" text rounded severity="danger" size="small"
            :aria-label="t('access.remove')" @click="revoke(r.relation, r.subject)"
          />
        </span>
      </div>

      <template v-if="can.share">
        <Divider />
        <div class="field">
          <label>{{ t('access.shareWith') }}</label>
          <SubjectPicker v-model="subject" />
        </div>
        <div class="row" style="flex-wrap: wrap">
          <Select v-model="relation" :options="relationOptions" option-label="label" option-value="value" style="min-width: 9rem" />
          <Button :label="t('access.share')" icon="pi pi-share-alt" :disabled="!subject" :loading="pending" @click="share" />
          <Button :label="t('access.transfer')" icon="pi pi-arrow-right-arrow-left" severity="secondary" outlined :disabled="!subject" @click="transfer" />
        </div>
      </template>
      <p v-else class="muted">{{ t('access.readOnly') }}</p>
    </template>
  </div>
</template>
