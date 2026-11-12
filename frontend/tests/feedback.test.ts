import { describe, expect, it } from 'vitest'
import { errorFeedback, successFeedback } from '../app/utils/feedback'

function httpError(status: number, data: object = {}, headers: Record<string, string> = {}) {
  return Object.assign(new Error(`${status}`), { data, statusCode: status, response: { status, headers: new Headers(headers) } })
}

describe('successFeedback', () => {
  it('stays quiet for reads unless a summary is given', () => {
    expect(successFeedback('GET')).toBeNull()
    expect(successFeedback('GET', 200, 'Exported')?.severity).toBe('success')
  })

  it('confirms writes, and treats 202 as info', () => {
    expect(successFeedback('PUT')).toMatchObject({ severity: 'success', summary: 'Saved' })
    expect(successFeedback('DELETE')).toMatchObject({ severity: 'success', summary: 'Deleted' })
    expect(successFeedback('POST', 202)).toMatchObject({ severity: 'info' })
  })
})

describe('errorFeedback', () => {
  it('says nothing on 401 (the api plugin handles the session)', () => {
    expect(errorFeedback(httpError(401))).toBeNull()
  })

  it('warns on errors the user can fix', () => {
    expect(errorFeedback(httpError(403, { detail: 'Missing user:write' }))).toMatchObject({ severity: 'warn', summary: 'Not allowed', detail: 'Missing user:write' })
    expect(errorFeedback(httpError(409, { detail: 'E-mail taken' }), 'Could not save')).toMatchObject({ severity: 'warn', summary: 'Could not save' })
    expect(errorFeedback(httpError(400, { detail: 'Validation failed', errors: { email: 'bad' } }))?.detail).toBe('Some fields need your attention.')
    expect(errorFeedback(httpError(429, {}, { 'Retry-After': '30' }))?.detail).toBe('Try again in 30 seconds.')
  })

  it('reports server faults as errors with the request id', () => {
    const fb = errorFeedback(httpError(500, { detail: 'Unexpected error', requestId: 'abc123' }))
    expect(fb).toMatchObject({ severity: 'error' })
    expect(fb?.detail).toContain('abc123')
  })

  it('reports a network failure as an error', () => {
    expect(errorFeedback(new TypeError('fetch failed'))).toMatchObject({ severity: 'error', summary: 'Can\'t reach the server' })
  })
})
