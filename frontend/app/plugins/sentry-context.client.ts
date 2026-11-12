import * as Sentry from '@sentry/nuxt'

/** Tags browser errors with the workspace and the signed-in user's id (never e-mail or name). */
export default defineNuxtPlugin(() => {
  const auth = useAuthStore()
  const tenant = useTenantStore()
  Sentry.setTag('host', window.location.hostname)

  watch(() => auth.user, (user) => {
    Sentry.setUser(user ? { id: user.id } : null)
    Sentry.setTag('tenant', user?.tenant)
    Sentry.setTag('impersonated', user?.impersonatedBy ? 'yes' : undefined)
  }, { immediate: true })

  watch(() => tenant.managedTenant, slug => Sentry.setTag('managed_tenant', slug ?? undefined), { immediate: true })
})
