import type { FetchError } from 'ofetch'
import { problemOf } from './problem'

/** PrimeVue Toast severities. */
export type Severity = 'success' | 'info' | 'warn' | 'error' | 'secondary' | 'contrast'

/** What the user should see after a request. `null` = say nothing. */
export interface Feedback {
  severity: Severity
  summary: string
  detail?: string
  life: number
}

export type HttpMethod = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

const LIFE = { success: 3000, info: 4000, warn: 6000, error: 8000 } as const

/** HTTP status of an ofetch error, or `undefined` when the request never got a response (offline, DNS, CORS...). */
export function statusOf(error: unknown): number | undefined {
  const e = error as FetchError | undefined
  return e?.response?.status ?? e?.statusCode ?? undefined
}

/**
 * Default success toast for a request. Reads never toast (the data on screen is the feedback); writes get a
 * short confirmation. A 202 means "queued", so it is info rather than success.
 */
export function successFeedback(method: HttpMethod, status = 200, summary?: string): Feedback | null {
  if (!summary && (method === 'GET' || method === 'HEAD')) return null
  if (status === 202) return { severity: 'info', summary: summary ?? 'Request accepted', life: LIFE.info }
  const fallback = method === 'DELETE' ? 'Deleted' : method === 'POST' ? 'Done' : 'Saved'
  return { severity: 'success', summary: summary ?? fallback, life: LIFE.success }
}

/**
 * Maps a failed request to a toast. The severity says whose problem it is:
 *  - warn  = the user can fix it (validation, conflict, permission, not found, slow down)
 *  - error = it is our fault or the network's (5xx, offline); the request id is shown so support can find it
 *  - null  = 401: the api plugin already refreshed the session or sent the user to the login page
 */
export function errorFeedback(error: unknown, summary?: string): Feedback | null {
  const status = statusOf(error)
  const problem = problemOf(error)

  if (status === 401) return null
  if (status === undefined) {
    return { severity: 'error', summary: summary ?? 'Can\'t reach the server', detail: 'Check your connection and try again.', life: LIFE.error }
  }
  if (status >= 500) {
    const ref = problem.requestId ? ` Reference: ${problem.requestId}.` : ''
    return { severity: 'error', summary: summary ?? 'Something went wrong on our side', detail: `${problem.detail}${ref}`, life: LIFE.error }
  }

  const titles: Record<number, string> = {
    400: 'Check the form', 403: 'Not allowed', 404: 'Not found', 409: 'Conflict', 413: 'Too large', 422: 'Check the form', 429: 'Too many requests',
  }
  let detail = problem.detail
  if (problem.errors && Object.keys(problem.errors).length) detail = 'Some fields need your attention.'
  if (status === 429) {
    const retry = (error as FetchError).response?.headers.get('Retry-After')
    if (retry) detail = `Try again in ${retry} seconds.`
  }
  return { severity: 'warn', summary: summary ?? titles[status] ?? 'Request failed', detail, life: LIFE.warn }
}
