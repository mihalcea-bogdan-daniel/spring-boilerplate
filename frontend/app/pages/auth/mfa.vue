<script setup lang="ts">
/** Second sign-in step for accounts with an authenticator app. The challenge token lives in the auth store. */
definePageMeta({ layout: 'auth', public: true })

const auth = useAuthStore()
const route = useRoute()
const code = ref('')
const useRecovery = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)

onMounted(() => {
  if (!auth.mfaToken) navigateTo('/auth/login') // page reloaded: the in-memory challenge is gone
})

async function submit() {
  loading.value = true
  error.value = null
  try {
    await auth.verifyMfa(code.value)
    await navigateTo(typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/')
  } catch (e) {
    const p = problemOf(e)
    if (p.code === 'mfa_expired') return navigateTo('/auth/login')
    error.value = p.detail
    code.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Card>
    <template #title>Two-factor authentication</template>
    <template #subtitle>
      {{ useRecovery ? 'Enter one of your recovery codes.' : 'Enter the 6-digit code from your authenticator app.' }}
    </template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <InputText v-if="useRecovery" v-model="code" placeholder="xxxxx-xxxxx" autocomplete="off" required fluid />
        <div v-else class="row" style="justify-content: center">
          <InputOtp v-model="code" :length="6" integer-only />
        </div>
        <Button type="submit" label="Verify" :loading="loading" :disabled="useRecovery ? !code : code.length !== 6" fluid />
        <Button
type="button" :label="useRecovery ? 'Use authenticator code' : 'Use a recovery code'" link
                @click="useRecovery = !useRecovery; code = ''" />
      </form>
    </template>
    <template #footer>
      <NuxtLink to="/auth/login" class="muted">Back to sign in</NuxtLink>
    </template>
  </Card>
</template>
