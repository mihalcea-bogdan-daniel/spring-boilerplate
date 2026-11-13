<script setup lang="ts">
/** Landing page after Spring's OAuth2 success handler: /auth/callback?code=...&redirect=/... */
definePageMeta({ layout: 'auth', public: true })

const { t } = useI18n()

const auth = useAuthStore()
const route = useRoute()
const error = ref<string | null>(typeof route.query.error === 'string' ? route.query.error : null)

onMounted(async () => {
  if (error.value) return
  if (typeof route.query.impersonate === 'string') {
    try {
      await auth.exchangeImpersonationCode(route.query.impersonate)
      await navigateTo('/', { replace: true })
    } catch (e) {
      error.value = problemOf(e).code ?? 'exchange_failed'
    }
    return
  }
  const code = route.query.code
  if (typeof code !== 'string') {
    error.value = 'missing_code'
    return
  }
  try {
    const signedIn = await auth.exchangeOAuth2Code(code)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/'
    await navigateTo(signedIn ? redirect : { path: '/auth/mfa', query: { redirect } }, { replace: true })
  } catch (e) {
    error.value = problemOf(e).code ?? 'exchange_failed'
  }
})
</script>

<template>
  <Card>
    <template #content>
      <div v-if="!error" class="stack" style="align-items: center">
        <ProgressSpinner style="width: 3rem; height: 3rem" />
        <p>{{ t('auth.completing') }}</p>
      </div>
      <div v-else class="stack">
        <Message severity="error" :closable="false">{{ t('auth.failed', { error }) }}</Message>
        <Button as="router-link" to="/auth/login" :label="t('auth.backToSignIn')" fluid />
      </div>
    </template>
  </Card>
</template>
