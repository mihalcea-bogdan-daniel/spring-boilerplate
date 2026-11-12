<script setup lang="ts">
import type { ApiKeyDto } from '~/types/api'

definePageMeta({ permission: 'apikey:manage' })

const auth = useAuthStore()
const { t, d } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const { request, pending: saving } = useRequest()

const { data: keys, pending, error, refresh } = useApi<ApiKeyDto[]>('/admin/api-keys', { server: false })

const dialog = ref(false)
const form = reactive<{ name: string, permissions: string[] }>({ name: '', permissions: [] })
/** The secret is returned once by the API and only kept here until the dialog closes. */
const createdKey = ref<string | null>(null)

function open() {
  Object.assign(form, { name: '', permissions: [] })
  createdKey.value = null
  dialog.value = true
}

async function create() {
  // no success toast: the dialog itself switches to showing the new secret
  const res = await request<{ apiKey: ApiKeyDto, key: string }>('/admin/api-keys', {
    method: 'POST', body: form, success: false, error: t('apiKeys.createFailed'),
  })
  if (!res.ok) return
  createdKey.value = res.data.key
  await refresh()
}

async function copy() {
  if (!createdKey.value) return
  await navigator.clipboard.writeText(createdKey.value)
  toast.add({ severity: 'success', summary: t('common.copied'), life: 2000 })
}

function revoke(k: ApiKeyDto) {
  confirm.require({
    header: t('apiKeys.revokeHeader'),
    message: t('apiKeys.revokeConfirm', { name: k.name }),
    acceptProps: { label: t('common.revoke'), severity: 'danger' },
    rejectProps: { label: t('common.cancel'), severity: 'secondary', outlined: true },
    accept: async () => {
      const res = await request(`/admin/api-keys/${k.id}`, { method: 'DELETE', success: t('apiKeys.revoked', { name: k.name }) })
      if (res.ok) await refresh()
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">{{ t('apiKeys.title') }}</h1>
      <Button :label="t('apiKeys.new')" icon="pi pi-plus" @click="open" />
    </div>
    <i18n-t keypath="apiKeys.intro" tag="p" class="muted">
      <template #header><code>X-API-Key</code></template>
    </i18n-t>

    <LoadError :error="error" :what="t('apiKeys.what')" @retry="refresh()" />
    <DataTable v-if="!error" :value="keys ?? []" :loading="pending" data-key="id">
      <template #empty>{{ t('apiKeys.empty') }}</template>
      <Column field="name" :header="t('common.name')" />
      <Column :header="t('apiKeys.key')"><template #body="{ data: k }"><code>{{ k.keyPrefix }}…</code></template></Column>
      <Column :header="t('common.permissions')">
        <template #body="{ data: k }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Chip v-for="p in k.permissions" :key="p" :label="p" /></div>
        </template>
      </Column>
      <Column :header="t('common.lastUsed')">
        <template #body="{ data: k }">{{ k.lastUsedAt ? d(k.lastUsedAt, 'datetime') : t('common.never') }}</template>
      </Column>
      <Column field="createdBy" :header="t('apiKeys.createdBy')" />
      <Column style="width: 4rem; text-align: right">
        <template #body="{ data: k }">
          <Button icon="pi pi-trash" text rounded severity="danger" :aria-label="t('common.revoke')" @click="revoke(k)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" :header="t('apiKeys.dialog')" modal :style="{ width: '32rem' }">
      <div v-if="createdKey" class="stack">
        <Message severity="warn" :closable="false">{{ t('apiKeys.copyNow') }}</Message>
        <code style="word-break: break-all">{{ createdKey }}</code>
        <div class="row" style="justify-content: flex-end">
          <Button :label="t('common.copy')" icon="pi pi-copy" severity="secondary" @click="copy" />
          <Button :label="t('common.done')" @click="dialog = false" />
        </div>
      </div>
      <form v-else class="stack" @submit.prevent="create">
        <div class="field">
          <label for="k-name">{{ t('common.name') }}</label>
          <InputText id="k-name" v-model="form.name" :placeholder="t('apiKeys.namePlaceholder')" required fluid />
        </div>
        <div class="field">
          <label>{{ t('common.permissions') }} <span class="muted">{{ t('apiKeys.grantHint') }}</span></label>
          <div v-for="p in auth.user?.permissions ?? []" :key="p" class="row">
            <Checkbox v-model="form.permissions" :input-id="`kp-${p}`" :value="p" />
            <label :for="`kp-${p}`"><code>{{ p }}</code></label>
          </div>
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="dialog = false" />
          <Button type="submit" :label="t('common.create')" icon="pi pi-key" :loading="saving" :disabled="!form.permissions.length" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
