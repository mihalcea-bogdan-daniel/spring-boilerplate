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

/** vue-i18n's `t`. Passed in so this file stays framework-free and unit-testable. Keys live under `feedback.*`. */
export type Translate = (key: string, params?: Record<string, unknown>) => string

const LIFE = { success: 3000, info: 4000, warn: 6000, error: 8000 } as const

/** HTTP status of an ofetch error, or `undefined` when the request never got a response (offline, DNS, CORS...). */
export function statusOf(error: unknown): number | undefined {
  const e = error as FetchError | undefined
  return e?.response?.status ?? e?.statusCode ?? undefined
}

/**
 * Default success toast for a request. Reads never toast (the data on screen is the feedback); writes get a
 * short confirmation. A 202 means "queued", so it is info rather than success.
 * `summary` is already translated by the caller.
 */
export function successFeedback(t: Translate, method: HttpMethod, status = 200, summary?: string): Feedback | null {
  if (!summary && (method === 'GET' || method === 'HEAD')) return null
  if (status === 202) return { severity: 'info', summary: summary ?? t('feedback.accepted'), life: LIFE.info }
  const fallback = method === 'DELETE' ? 'feedback.deleted' : method === 'POST' ? 'feedback.done' : 'feedback.saved'
  return { severity: 'success', summary: summary ?? t(fallback), life: LIFE.success }
}

const TITLES: Record<number, string> = {
  400: 'feedback.badRequest',
  403: 'feedback.forbidden',
  404: 'feedback.notFound',
  409: 'feedback.conflict',
  413: 'feedback.tooLarge',
  422: 'feedback.badRequest',
  429: 'feedback.tooManyRequests',
}

/**
 * Maps a failed request to a toast. The severity says whose problem it is:
 *  - warn  = the user can fix it (validation, conflict, permission, not found, slow down)
 *  - error = it is our fault or the network's (5xx, offline); the request id is shown so support can find it
 *  - null  = 401: the api plugin already refreshed the session or sent the user to the login page
 * The server's `detail` is shown as is (see docs/i18n.md for server-side texts).
 */
export function errorFeedback(t: Translate, error: unknown, summary?: string): Feedback | null {
  const status = statusOf(error)
  const problem = problemOf(error)

  if (status === 401) return null
  if (status === undefined) {
    return { severity: 'error', summary: summary ?? t('feedback.offline'), detail: t('feedback.offlineDetail'), life: LIFE.error }
  }
  if (status >= 500) {
    const ref = problem.requestId ? ` ${t('feedback.reference', { id: problem.requestId })}` : ''
    return { severity: 'error', summary: summary ?? t('feedback.serverError'), detail: `${problem.detail}${ref}`, life: LIFE.error }
  }

  let detail = problem.detail
  if (problem.errors && Object.keys(problem.errors).length) detail = t('feedback.fieldErrors')
  if (status === 429) {
    const retry = (error as FetchError).response?.headers.get('Retry-After')
    if (retry) detail = t('feedback.retryAfter', { seconds: retry })
  }
  return { severity: 'warn', summary: summary ?? t(TITLES[status] ?? 'feedback.failed'), detail, life: LIFE.warn }
}
