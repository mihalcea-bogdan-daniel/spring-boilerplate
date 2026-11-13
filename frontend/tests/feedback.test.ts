import { describe, expect, it } from 'vitest'
import { errorFeedback, successFeedback, type Translate } from '../app/utils/feedback'
import en from '../i18n/locales/en.json'

/** Minimal vue-i18n stand-in: looks keys up in en.json and fills {params}. Fails the test on a missing key. */
const t: Translate = (key, params = {}) => {
  const msg = key.split('.').reduce<unknown>((node, k) => (node as Record<string, unknown>)?.[k], en)
  if (typeof msg !== 'string') throw new Error(`missing i18n key ${key}`)
  return msg.replace(/\{(\w+)\}/g, (_, p: string) => String(params[p]))
}

function httpError(status: number, data: object = {}, headers: Record<string, string> = {}) {
  return Object.assign(new Error(`${status}`), { data, statusCode: status, response: { status, headers: new Headers(headers) } })
}

describe('successFeedback', () => {
  it('stays quiet for reads unless a summary is given', () => {
    expect(successFeedback(t, 'GET')).toBeNull()
    expect(successFeedback(t, 'GET', 200, 'Exported')?.severity).toBe('success')
  })

  it('confirms writes, and treats 202 as info', () => {
    expect(successFeedback(t, 'PUT')).toMatchObject({ severity: 'success', summary: 'Saved' })
    expect(successFeedback(t, 'DELETE')).toMatchObject({ severity: 'success', summary: 'Deleted' })
    expect(successFeedback(t, 'POST', 202)).toMatchObject({ severity: 'info' })
  })
})

describe('errorFeedback', () => {
  it('says nothing on 401 (the api plugin handles the session)', () => {
    expect(errorFeedback(t, httpError(401))).toBeNull()
  })

  it('warns on errors the user can fix', () => {
    expect(errorFeedback(t, httpError(403, { detail: 'Missing user:write' }))).toMatchObject({ severity: 'warn', summary: 'Not allowed', detail: 'Missing user:write' })
    expect(errorFeedback(t, httpError(409, { detail: 'E-mail taken' }), 'Could not save')).toMatchObject({ severity: 'warn', summary: 'Could not save' })
    expect(errorFeedback(t, httpError(400, { detail: 'Validation failed', errors: { email: 'bad' } }))?.detail).toBe('Some fields need your attention.')
    expect(errorFeedback(t, httpError(429, {}, { 'Retry-After': '30' }))?.detail).toBe('Try again in 30 seconds.')
  })

  it('reports server faults as errors with the request id', () => {
    const fb = errorFeedback(t, httpError(500, { detail: 'Unexpected error', requestId: 'abc123' }))
    expect(fb).toMatchObject({ severity: 'error' })
    expect(fb?.detail).toContain('abc123')
  })

  it('reports a network failure as an error', () => {
    expect(errorFeedback(t, new TypeError('fetch failed'))).toMatchObject({ severity: 'error', summary: 'Can\'t reach the server' })
  })
})
