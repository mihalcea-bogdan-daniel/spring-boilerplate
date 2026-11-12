<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

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

const providerIcon = (p: string) => ({ google: 'pi pi-google', github: 'pi pi-github' }[p] ?? 'pi pi-sign-in')
</script>

<template>
  <Card>
    <template #title>Sign in</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="route.query.error" severity="error" :closable="false">
          Social sign-in failed ({{ route.query.error }}).
        </Message>
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>

        <div class="field">
          <label for="email">E-mail</label>
          <InputText id="email" v-model="form.email" type="email" autocomplete="username" required fluid />
        </div>
        <div class="field">
          <div class="row-between">
            <label for="password">Password</label>
            <NuxtLink to="/auth/forgot-password" class="muted">Forgot?</NuxtLink>
          </div>
          <Password id="password" v-model="form.password" :feedback="false" toggle-mask
                    autocomplete="current-password" required fluid />
        </div>
        <Button type="submit" label="Sign in" icon="pi pi-sign-in" :loading="loading" fluid />
        <Button as="router-link" to="/auth/otp" label="E-mail me a sign-in code" severity="secondary" outlined fluid />

        <Divider v-if="providers.length" align="center"><span class="muted">or continue with</span></Divider>
        <div class="grid-2">
          <Button v-for="p in providers" :key="p" :icon="providerIcon(p)" :label="p" severity="contrast" outlined
                  style="text-transform: capitalize" @click="auth.startSocialLogin(p, redirect)" />
        </div>
      </form>
    </template>
    <template #footer>
      <p v-if="branding.allowSelfRegistration" class="muted">No account? <NuxtLink to="/auth/register">Create one</NuxtLink></p>
    </template>
  </Card>
</template>
