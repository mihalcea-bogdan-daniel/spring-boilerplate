import type { Problem } from '~/types/api'
import type { HttpMethod } from '~/utils/feedback'

type ApiOptions = NonNullable<Parameters<typeof $fetch>[1]>

export interface RequestOptions<T> extends Omit<ApiOptions, 'method'> {
  method?: HttpMethod
  /**
   * Success toast. Default: none for GET, "Saved"/"Done"/"Deleted" for writes.
   * A string or `(data) => string` sets the summary; `false` keeps quiet.
   */
  success?: string | ((data: T) => string) | false
  /** Error toast summary, e.g. "Could not save the user". The server's `detail` is shown under it. `false` = no toast. */
  error?: string | false
}

export type RequestResult<T> =
  | { ok: true, data: T }
  | { ok: false, problem: Problem & { detail: string }, status: number | undefined }

/**
 * The one way to call the API from an event handler (save, delete, invite...). It never throws:
 *
 *   const { request, pending, fieldErrors } = useRequest()
 *   const res = await request<UserDto>('/admin/users', { method: 'POST', body: form, success: 'User created' })
 *   if (res.ok) dialog.value = false
 *
 * - toasts the outcome with a severity derived from the HTTP status (see utils/feedback.ts)
 * - fills `fieldErrors` from the problem's `errors` map, so `<small>{{ fieldErrors.email }}</small>` just works
 * - `pending` is true while any request from this instance is in flight (bind it to the submit button)
 *
 * For data shown on page load use `useApi()` + `<LoadError>` instead; that is a state, not a toast.
 */
export function useRequest() {
  const { $api } = useNuxtApp()
  const toast = useToast()
  const inFlight = ref(0)
  const pending = computed(() => inFlight.value > 0)
  const fieldErrors = ref<Record<string, string>>({})

  async function request<T = unknown>(path: string, options: RequestOptions<T> = {}): Promise<RequestResult<T>> {
    const { success, error, ...fetchOptions } = options
    const method = (fetchOptions.method ?? 'GET').toUpperCase() as HttpMethod
    let status = 200

    inFlight.value++
    fieldErrors.value = {}
    try {
      const data = await $api<T>(path, {
        ...fetchOptions,
        method,
        onResponse: ({ response }) => { status = response.status },
      })
      if (success !== false) {
        const summary = typeof success === 'function' ? success(data) : success
        const fb = successFeedback(method, status, summary)
        if (fb) toast.add(fb)
      }
      return { ok: true, data }
    } catch (e) {
      const problem = problemOf(e)
      fieldErrors.value = problem.errors ?? {}
      if (error !== false) {
        const fb = errorFeedback(e, error)
        if (fb) toast.add(fb)
      }
      return { ok: false, problem, status: statusOf(e) }
    } finally {
      inFlight.value--
    }
  }

  /**
   * Same error toast for a failure that did not go through request(), e.g. a store action:
   *   try { await auth.updateProfile(...) } catch (e) { notifyError(e, 'Could not update the profile') }
   */
  function notifyError(e: unknown, summary?: string) {
    fieldErrors.value = problemOf(e).errors ?? {}
    const fb = errorFeedback(e, summary)
    if (fb) toast.add(fb)
  }

  return { request, pending, fieldErrors, notifyError }
}
