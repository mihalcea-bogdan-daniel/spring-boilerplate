<script setup lang="ts">
definePageMeta({ permission: 'settings:read' })

const auth = useAuthStore()
const toast = useToast()
const { $api } = useNuxtApp()

const { data, pending, error, refresh } = useApi<Record<string, string | null>>('/admin/settings', { server: false })

const form = reactive({ displayName: '', primaryColor: 'emerald', logoUrl: '', allowSelfRegistration: true, allowSocialLogin: true })
const branding = useBranding()
const palettes = ['emerald', 'green', 'lime', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose', 'red', 'orange', 'amber', 'yellow', 'slate', 'zinc']
watch(data, (s) => {
  if (!s) return
  form.displayName = s['general.displayName'] ?? ''
  form.primaryColor = s['brand.primaryColor'] ?? 'emerald'
  form.logoUrl = s['brand.logoUrl'] ?? ''
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
        'brand.primaryColor': form.primaryColor,
        'brand.logoUrl': form.logoUrl || null,
        'auth.allowSelfRegistration': String(form.allowSelfRegistration),
        'auth.allowSocialLogin': String(form.allowSocialLogin),
      },
    })
    toast.add({ severity: 'success', summary: 'Settings saved', life: 3000 })
    await branding.load(true) // apply the new look immediately
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
    <LoadError :error="error" what="settings" @retry="refresh()" />
    <Card v-if="!error">
      <template #content>
        <form class="stack" @submit.prevent="save">
          <div class="field">
            <label for="s-name">Workspace display name</label>
            <InputText id="s-name" v-model="form.displayName" :disabled="pending" fluid />
          </div>
          <div class="grid-2">
            <div class="field">
              <label for="s-color">Brand colour</label>
              <Select id="s-color" v-model="form.primaryColor" :options="palettes" fluid>
                <template #option="{ option }">
                  <span class="row"><span :style="{ width: '1rem', height: '1rem', borderRadius: '50%', background: `var(--p-${option}-500)` }" /> {{ option }}</span>
                </template>
              </Select>
            </div>
            <div class="field">
              <label for="s-logo">Logo URL <span class="muted">(https)</span></label>
              <InputText id="s-logo" v-model="form.logoUrl" placeholder="https://cdn.example.com/logo.svg" fluid />
            </div>
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
            <Button type="submit" label="Save" icon="pi pi-check" :loading="saving" :disabled="!auth.can('settings:write') || pending || !data" />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>
