import type { UseFetchOptions } from 'nuxt/app'

/**
 * useFetch bound to the $api client (auth + tenant headers + refresh), e.g.
 *   const { data, refresh } = useApi<PageResponse<UserDto>>('/admin/users', { query: { page } })
 */
export function useApi<T>(url: string | (() => string), options: UseFetchOptions<T> = {}) {
  return useFetch(url, {
    ...options,
    $fetch: useNuxtApp().$api as typeof $fetch,
  })
}
