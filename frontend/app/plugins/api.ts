import type { FetchError } from 'ofetch'

type ApiOptions = NonNullable<Parameters<typeof $fetch>[1]>

/**
 * Typed `$api` client used everywhere instead of raw $fetch:
 *  - Authorization: Bearer <access token>
 *  - X-Tenant-ID: <slug> when a super-admin manages another tenant (never on /auth/* calls)
 *  - SSR: talks to the backend directly and forwards the public host so Spring can resolve the tenant
 *  - 401 => one shared refresh-token rotation, then the original request is replayed once
 */
export default defineNuxtPlugin({
  name: 'api',
  setup(nuxtApp) {
  const config = useRuntimeConfig()
  const router = useRouter()
  const auth = useAuthStore()
  const tenant = useTenantStore()

  const ssrHeaders = import.meta.server
    ? useRequestHeaders(['host', 'x-forwarded-host', 'x-forwarded-proto', 'user-agent'])
    : {}

  /** /auth/* endpoints are anonymous: an expired bearer would make Spring reject them, so don't send it. */
  const isAnonymousAuthCall = (path: string) => path.startsWith('/auth/') && path !== '/auth/me'

  const client = $fetch.create({
    baseURL: import.meta.server ? config.apiInternalBase : config.public.apiBase,
    credentials: 'include',
    retry: false,
    onRequest({ request, options }) {
      const path = typeof request === 'string' ? request : request.url
      const headers = options.headers

      if (auth.token && !isAnonymousAuthCall(path) && !headers.has('Authorization')) {
        headers.set('Authorization', `Bearer ${auth.token}`)
      }
      // /auth/*, /me and the platform console always act on the user's own (home) tenant
      const isOwnAccountCall = path.startsWith('/auth/') || path === '/me' || path.startsWith('/me/')
        || path.startsWith('/platform/')
      // lets Spring pick the language of validation messages (LocaleContextHolder)
      if (!headers.has('Accept-Language')) headers.set('Accept-Language', nuxtApp.$i18n.locale.value)
      if (tenant.headerValue && !isOwnAccountCall) {
        headers.set(config.public.tenantHeader, tenant.headerValue)
      }
      if (import.meta.server) {
        const host = ssrHeaders['x-forwarded-host'] ?? ssrHeaders.host
        if (host) headers.set('X-Forwarded-Host', host)
        if (ssrHeaders['x-forwarded-proto']) headers.set('X-Forwarded-Proto', ssrHeaders['x-forwarded-proto'])
        if (ssrHeaders['user-agent']) headers.set('User-Agent', ssrHeaders['user-agent'])
      }
    },
  })

  async function api<T = unknown>(path: string, options: ApiOptions = {}): Promise<T> {
    try {
      return await client<T>(path, options)
    } catch (e) {
      const status = (e as FetchError).response?.status
      if (status === 401 && import.meta.client && !isAnonymousAuthCall(path)) {
        if (await auth.refresh()) return client<T>(path, options)
        const redirect = router.currentRoute.value.fullPath
        await nuxtApp.runWithContext(() => navigateTo({ path: '/auth/login', query: { redirect } }))
      }
      throw e
    }
  }

  return { provide: { api } }
  },
})
