<script setup lang="ts">
definePageMeta({ permission: 'settings:read' })

const auth = useAuthStore()
const { t } = useI18n()
const { request, pending: saving } = useRequest()

const { data, pending, error, refresh } = useApi<Record<string, string | null>>('/admin/settings', { server: false })

const form = reactive({ displayName: '', primaryColor: 'brand', logoUrl: '', allowSelfRegistration: true, allowSocialLogin: true })
const branding = useBranding()
// 'brand' is the product accent (app/theme/brand.ts); the rest are Aura palettes
const palettes = ['brand', 'emerald', 'green', 'lime', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose', 'red', 'orange', 'amber', 'yellow', 'slate', 'zinc']
watch(data, (s) => {
  if (!s) return
  form.displayName = s['general.displayName'] ?? ''
  form.primaryColor = s['brand.primaryColor'] ?? 'brand'
  form.logoUrl = s['brand.logoUrl'] ?? ''
  form.allowSelfRegistration = s['auth.allowSelfRegistration'] !== 'false'
  form.allowSocialLogin = s['auth.allowSocialLogin'] !== 'false'
}, { immediate: true })

async function save() {
  const res = await request('/admin/settings', {
    method: 'PUT',
    body: {
      'general.displayName': form.displayName || null,
      'brand.primaryColor': form.primaryColor,
      'brand.logoUrl': form.logoUrl || null,
      'auth.allowSelfRegistration': String(form.allowSelfRegistration),
      'auth.allowSocialLogin': String(form.allowSocialLogin),
    },
    success: t('settings.saved'),
    error: t('settings.saveFailed'),
  })
  if (!res.ok) return
  await branding.load(true) // apply the new look immediately
  await refresh()
}
</script>

<template>
  <div class="stack" style="max-width: 44rem">
    <h1 class="page-title">{{ t('settings.title') }}</h1>
    <LoadError :error="error" :what="t('settings.what')" @retry="refresh()" />
    <Card v-if="!error">
      <template #content>
        <form class="stack" @submit.prevent="save">
          <div class="field">
            <label for="s-name">{{ t('settings.displayName') }}</label>
            <InputText id="s-name" v-model="form.displayName" :disabled="pending" fluid />
          </div>
          <div class="grid-2">
            <div class="field">
              <label for="s-color">{{ t('settings.brandColour') }}</label>
              <Select id="s-color" v-model="form.primaryColor" :options="palettes" fluid>
                <template #option="{ option }">
                  <span class="row"><span :style="{ width: '1rem', height: '1rem', borderRadius: '50%', background: `var(--p-${option}-500)` }" /> {{ option }}</span>
                </template>
              </Select>
            </div>
            <div class="field">
              <label for="s-logo">{{ t('settings.logoUrl') }} <span class="muted">(https)</span></label>
              <InputText id="s-logo" v-model="form.logoUrl" placeholder="https://cdn.example.com/logo.svg" fluid />
            </div>
          </div>
          <div class="row-between">
            <div>
              <strong>{{ t('settings.selfRegistration') }}</strong>
              <p class="muted">{{ t('settings.selfRegistrationHint') }}</p>
            </div>
            <ToggleSwitch v-model="form.allowSelfRegistration" />
          </div>
          <div class="row-between">
            <div>
              <strong>{{ t('settings.socialLogin') }}</strong>
              <p class="muted">{{ t('settings.socialLoginHint') }}</p>
            </div>
            <ToggleSwitch v-model="form.allowSocialLogin" />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="!auth.can('settings:write') || pending || !data" />
          </div>
        </form>
      </template>
    </Card>
  </div>
</template>
