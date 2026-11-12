import { defineStore } from 'pinia'

/**
 * Tenant targeting.
 * Regular users: the tenant IS the host (acme.example.com) - nothing to send.
 * Super-admins (on admin.example.com): may "enter" a tenant; every admin API call then carries X-Tenant-ID.
 */
export const useTenantStore = defineStore('tenant', () => {
  const managedTenant = useCookie<string | null>('managed_tenant', {
    sameSite: 'strict',
    secure: !import.meta.dev,
    default: () => null,
  })

  /** Value for the X-Tenant-ID header, or null to let the host decide. */
  const headerValue = computed<string | null>(() => {
    const auth = useAuthStore()
    return auth.isSuperAdmin ? managedTenant.value : null
  })

  function manage(slug: string | null) {
    managedTenant.value = slug
  }

  function reset() {
    managedTenant.value = null
  }

  return { managedTenant, headerValue, manage, reset }
})
