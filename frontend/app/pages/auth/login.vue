<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const { t } = useI18n()

const auth = useAuthStore()
const route = useRoute()
const { branding } = useBranding()
// tenants can switch social login and self-registration off in their settings
const providers = computed(() => (branding.value.allowSocialLogin ? useRuntimeConfig().public.oauth2Providers as string[] : []))

const form = reactive({ email: '', password: '' })
const loading = ref(false)
const error = ref<string | null>(null)
const redirect = computed(() => (typeof route.query.redirect === 'string' ? route.query.redirect : '/'))

async function submit() {
  loading.value = true
  error.value = null
  try {
    const signedIn = await auth.login(form.email, form.password)
    await navigateTo(signedIn ? redirect.value : { path: '/auth/mfa', query: { redirect: redirect.value } })
  } catch (e) {
    const p = problemOf(e)
    if (p.code === 'email_not_verified') {
      return navigateTo({ path: '/auth/verify-email', query: { email: form.email } })
    }
    error.value = p.detail
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- passkey sign-in
const passkeys = usePasskeys()
const passkeyLoading = ref(false)
async function passkeySignIn() {
  passkeyLoading.value = true
  error.value = null
  try {
    await passkeys.signIn()
    await navigateTo(redirect.value)
  } catch (e) {
    error.value = passkeys.messageOf(e)
  } finally {
    passkeyLoading.value = false
  }
}

const providerIcon = (p: string) => ({ google: 'pi pi-google', github: 'pi pi-github' }[p] ?? 'pi pi-sign-in')
</script>

<template>
  <Card>
    <template #title>{{ t('auth.signIn') }}</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="route.query.error" severity="error" :closable="false">
          {{ t('auth.socialFailed', { error: route.query.error }) }}
        </Message>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

        <div class="field">
          <label for="email">{{ t('common.email') }}</label>
          <InputText id="email" v-model="form.email" type="email" autocomplete="username" required fluid />
        </div>
        <div class="field">
          <div class="row-between">
            <label for="password">{{ t('common.password') }}</label>
            <NuxtLink to="/auth/forgot-password" class="muted">{{ t('auth.forgot') }}</NuxtLink>
          </div>
          <Password
id="password" v-model="form.password" :feedback="false" toggle-mask
                    autocomplete="current-password" required fluid />
        </div>
        <Button type="submit" :label="t('auth.signIn')" icon="pi pi-sign-in" :loading="loading" fluid />
        <Button
v-if="passkeys.supported.value" type="button" :label="t('auth.passkey')" icon="pi pi-key"
                severity="secondary" :loading="passkeyLoading" fluid @click="passkeySignIn" />
        <Button as="router-link" to="/auth/otp" :label="t('auth.emailCode')" severity="secondary" outlined fluid />

        <Divider v-if="providers.length" align="center"><span class="muted">{{ t('auth.orContinue') }}</span></Divider>
        <div class="grid-2">
          <Button
v-for="p in providers" :key="p" :icon="providerIcon(p)" :label="p" severity="contrast" outlined
                  style="text-transform: capitalize" @click="auth.startSocialLogin(p, redirect)" />
        </div>
      </form>
    </template>
    <template #footer>
      <p v-if="branding.allowSelfRegistration" class="muted">{{ t('auth.noAccount') }} <NuxtLink to="/auth/register">{{ t('auth.createOne') }}</NuxtLink></p>
    </template>
  </Card>
</template>
