<script setup lang="ts">
import type { ApiKeyDto } from '~/types/api'

definePageMeta({ permission: 'apikey:manage' })

const auth = useAuthStore()
const toast = useToast()
const confirm = useConfirm()
const { $api } = useNuxtApp()

const { data: keys, pending, refresh } = useApi<ApiKeyDto[]>('/admin/api-keys', { server: false })

const dialog = ref(false)
const saving = ref(false)
const form = reactive<{ name: string, permissions: string[] }>({ name: '', permissions: [] })
/** The secret is returned once by the API and only kept here until the dialog closes. */
const createdKey = ref<string | null>(null)

function open() {
  Object.assign(form, { name: '', permissions: [] })
  createdKey.value = null
  dialog.value = true
}

async function create() {
  saving.value = true
  try {
    const res = await $api<{ apiKey: ApiKeyDto, key: string }>('/admin/api-keys', { method: 'POST', body: form })
    createdKey.value = res.key
    await refresh()
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 6000 })
  } finally {
    saving.value = false
  }
}

async function copy() {
  if (!createdKey.value) return
  await navigator.clipboard.writeText(createdKey.value)
  toast.add({ severity: 'success', summary: 'Copied', life: 2000 })
}

function revoke(k: ApiKeyDto) {
  confirm.require({
    header: 'Revoke API key',
    message: `Revoke "${k.name}"? Integrations using it stop working immediately.`,
    acceptProps: { label: 'Revoke', severity: 'danger' },
    rejectProps: { label: 'Cancel', severity: 'secondary', outlined: true },
    accept: async () => {
      try {
        await $api(`/admin/api-keys/${k.id}`, { method: 'DELETE' })
        await refresh()
      } catch (e) {
        toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
      }
    },
  })
}
</script>

<template>
  <div class="stack">
    <div class="row-between">
      <h1 class="page-title">API keys</h1>
      <Button label="New key" icon="pi pi-plus" @click="open" />
    </div>
    <p class="muted">
      For scripts and integrations: send the key in the <code>X-API-Key</code> header to this workspace's address.
    </p>

    <DataTable :value="keys ?? []" :loading="pending" data-key="id">
      <template #empty>No API keys yet.</template>
      <Column field="name" header="Name" />
      <Column header="Key"><template #body="{ data: k }"><code>{{ k.keyPrefix }}…</code></template></Column>
      <Column header="Permissions">
        <template #body="{ data: k }">
          <div class="row" style="flex-wrap: wrap; gap: .25rem"><Chip v-for="p in k.permissions" :key="p" :label="p" /></div>
        </template>
      </Column>
      <Column header="Last used">
        <template #body="{ data: k }">{{ k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : 'never' }}</template>
      </Column>
      <Column field="createdBy" header="Created by" />
      <Column style="width: 4rem; text-align: right">
        <template #body="{ data: k }">
          <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Revoke" @click="revoke(k)" />
        </template>
      </Column>
    </DataTable>

    <Dialog v-model:visible="dialog" header="New API key" modal :style="{ width: '32rem' }">
      <div v-if="createdKey" class="stack">
        <Message severity="warn" :closable="false">Copy the key now — it will not be shown again.</Message>
        <code style="word-break: break-all">{{ createdKey }}</code>
        <div class="row" style="justify-content: flex-end">
          <Button label="Copy" icon="pi pi-copy" severity="secondary" @click="copy" />
          <Button label="Done" @click="dialog = false" />
        </div>
      </div>
      <form v-else class="stack" @submit.prevent="create">
        <div class="field">
          <label for="k-name">Name</label>
          <InputText id="k-name" v-model="form.name" placeholder="Nightly export" required fluid />
        </div>
        <div class="field">
          <label>Permissions <span class="muted">(you can only grant what you hold yourself)</span></label>
          <div v-for="p in auth.user?.permissions ?? []" :key="p" class="row">
            <Checkbox v-model="form.permissions" :input-id="`kp-${p}`" :value="p" />
            <label :for="`kp-${p}`"><code>{{ p }}</code></label>
          </div>
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="dialog = false" />
          <Button type="submit" label="Create" icon="pi pi-key" :loading="saving" :disabled="!form.permissions.length" />
        </div>
      </form>
    </Dialog>
  </div>
</template>
