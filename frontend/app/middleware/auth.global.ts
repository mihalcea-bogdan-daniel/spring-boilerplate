/**
 * Global guard. Pages opt out with definePageMeta({ public: true }) and can require
 * definePageMeta({ permission: 'user:read' }) or { superAdminOnly: true }.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    // signed-in users don't need the login screens
    if (auth.isAuthenticated && to.path.startsWith('/auth/') && to.path !== '/auth/callback') {
      return navigateTo('/')
    }
    return
  }

  // On the server we can only use the access-token cookie (the refresh cookie is scoped to /api/auth).
  // If it's missing, defer to the client pass, which can rotate the refresh token.
  if (import.meta.server && !auth.accessToken) return

  if (!(await auth.ensureSession())) {
    return navigateTo({ path: '/auth/login', query: { redirect: to.fullPath } })
  }
  if (to.meta.superAdminOnly && !auth.isSuperAdmin) {
    return abortNavigation(createError({ statusCode: 403, statusMessage: 'Forbidden' }))
  }
  if (to.meta.permission && !auth.can(to.meta.permission)) {
    return abortNavigation(createError({ statusCode: 403, statusMessage: 'Forbidden' }))
  }
})
