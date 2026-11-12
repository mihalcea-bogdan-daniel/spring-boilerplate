<script setup lang="ts">
definePageMeta({ permission: 'settings:read' })

const auth = useAuthStore()
const toast = useToast()
const { $api } = useNuxtApp()

const { data, pending, refresh } = useApi<Record<string, string | null>>('/admin/settings', { server: false })

const form = reactive({ displayName: '', allowSelfRegistration: true, allowSocialLogin: true })
watch(data, (s) => {
  if (!s) return
  form.displayName = s['general.displayName'] ?? ''
  form.allowSelfRegistration = s['auth.allowSelfRegistration'] !== 'false'
  form.allowSocialLogin = s['auth.allowSocialLogin'] !== 'false'
}, { immediate: true })

const saving = ref(false)
async function save() {
  saving.value = true
  try {
    await $api('/admin/settings', {
      method: 'PUT',
      body: {
        'general.displayName': form.displayName || null,
        'auth.allowSelfRegistration': String(form.allowSelfRegistration),
        'auth.allowSocialLogin': String(form.allowSocialLogin),
      },
    })
    toast.add({ severity: 'success', summary: 'Settings saved', life: 3000 })
    await refresh()
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="stack" style="max-width: 44rem">
    <h1 class="page-title">Settings</h1>
    <Card>
      <template #content>
        <form class="stack" @submit.prevent="save">
          <div class="field">
            <label for="s-name">Workspace display name</label>
            <InputText id="s-name" v-model="form.displayName" :disabled="pending" fluid />
          </div>
          <div class="row-between">
            <div>
              <strong>Self-registration</strong>
              <p class="muted">Allow anyone to create an account on this workspace.</p>
            </div>
            <ToggleSwitch v-model="form.allowSelfRegistration" />
          </div>
          <div class="row-between">
            <div>
              <strong>Social login</strong>
              <p class="muted">Allow Google / GitHub sign-in.</p>
            </div>
            <ToggleSwitch v-model="form.allowSocialLogin" />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" label="Save" icon="pi pi-check" :loading="saving" :disabled="!auth.can('settings:write')" />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>
