<script setup lang="ts">
import type { RankDto } from '~/types/api'

definePageMeta({ permission: 'org:manage' })

const { t } = useI18n()
const confirm = useConfirm()
const { ranks, label, pending, error, refresh } = useRanks()
const { request, pending: saving, fieldErrors } = useRequest()

const dialog = ref(false)
const form = reactive<{ id?: string, key: string, level: number, en: string, ro: string, aliases: string[], supervises: boolean }>(
  { key: '', level: 10, en: '', ro: '', aliases: [], supervises: false })

function open(r?: RankDto) {
  Object.assign(form, r
    ? { id: r.id, key: r.key, level: r.level, en: r.labels.en ?? '', ro: r.labels.ro ?? '', aliases: [...r.aliases], supervises: r.supervises }
    : { id: undefined, key: '', level: 10, en: '', ro: '', aliases: [], supervises: false })
  dialog.value = true
}

async function save() {
  const labels: Record<string, string> = { en: form.en }
  if (form.ro) labels.ro = form.ro
  const body = { key: form.key, level: form.level, labels, aliases: form.aliases, supervises: form.supervises }
  const res = await request(form.id ? `/admin/org/ranks/${form.id}` : '/admin/org/ranks', {
    method: form.id ? 'PUT' : 'POST', body, success: t('ranks.saved'), error: t('ranks.saveFailed'),
  })
  if (!res.ok) return
  dialog.value = false
  await refresh()
}

function remove(r: RankDto) {
  confirm.require({
    header: t('ranks.deleteHeader'),
    message: t('ranks.deleteConfirm', { name: label(r) }),
    acceptProps: { label: t('common.delete'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/org/ranks/${r.id}`, { method: 'DELETE', success: t('ranks.deleted', { name: label(r) }) })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('ranks.title') }}</h1>
      <Button :label="t('ranks.new')" icon="pi pi-plus" @click="open()" />
    </div>
    <p class="muted">{{ t('ranks.intro') }}</p>

    <LoadError :error="error" :what="t('ranks.what')" @retry="refresh()" />
    <DataTable v-if="!error" :value="ranks" :loading="pending" data-key="id">
      <template #empty>{{ t('ranks.empty') }}</template>
      <Column field="level" :header="t('ranks.level')" style="width: 6rem" />
      <Column :header="t('common.name')">
        <template #body="{ data: r }">
          <div>{{ r.labels.en }} <span v-if="r.labels.ro" class="muted">· {{ r.labels.ro }}</span></div>
          <code class="muted">{{ r.key }}</code>
        </template>
      </Column>
      <Column :header="t('ranks.aliases')">
        <template #body="{ data: r }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Chip v-for="a in r.aliases" :key="a" :label="a" /></div>
        </template>
      </Column>
      <Column :header="t('ranks.supervises')" style="width: 8rem">
        <template #body="{ data: r }">
          <Tag :value="r.supervises ? t('ranks.yes') : t('ranks.no')" :severity="r.supervises ? 'success' : 'secondary'" />
        </template>
      </Column>
      <Column style="width: 7rem; text-align: right">
        <template #body="{ data: r }">
          <Button icon="pi pi-pencil" text rounded :aria-label="t('common.edit')" @click="open(r)" />
          <Button icon="pi pi-trash" text rounded severity="danger" :aria-label="t('common.delete')" @click="remove(r)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="form.id ? t('ranks.edit') : t('ranks.new')" modal :style="{ width: '32rem' }">
      <form class="stack" @submit.prevent="save">
        <div class="grid-2">
          <div class="field">
            <label for="r-key">{{ t('ranks.key') }}</label>
            <InputText id="r-key" v-model="form.key" :invalid="!!fieldErrors.key" required fluid />
            <small v-if="fieldErrors.key" class="error">{{ fieldErrors.key }}</small>
          </div>
          <div class="field">
            <label for="r-level">{{ t('ranks.level') }}</label>
            <InputNumber id="r-level" v-model="form.level" :min="0" :max="10000" :use-grouping="false" fluid />
          </div>
        </div>
        <div class="grid-2">
          <div class="field">
            <label for="r-en">{{ t('ranks.nameEn') }}</label>
            <InputText id="r-en" v-model="form.en" required fluid />
          </div>
          <div class="field">
            <label for="r-ro">{{ t('ranks.nameRo') }}</label>
            <InputText id="r-ro" v-model="form.ro" fluid />
          </div>
        </div>
        <div class="field">
          <label for="r-aliases">{{ t('ranks.aliases') }}</label>
          <AutoComplete id="r-aliases" v-model="form.aliases" multiple :typeahead="false" fluid />
          <small class="muted">{{ t('ranks.aliasesHint') }}</small>
        </div>
        <div class="row-between">
          <div>
            <strong>{{ t('ranks.supervises') }}</strong>
            <p class="muted">{{ t('ranks.supervisesHint') }}</p>
          </div>
          <ToggleSwitch v-model="form.supervises" />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="dialog = false" />
          <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
