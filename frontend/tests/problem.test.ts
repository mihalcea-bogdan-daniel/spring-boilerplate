import { describe, expect, it } from 'vitest'
import { problemOf } from '../app/utils/problem'

describe('problemOf', () => {
  it('reads detail, code and field errors from a problem+json body', () => {
    const error = Object.assign(new Error('400 Bad Request'), {
      data: { status: 400, detail: 'Validation failed', code: 'validation', errors: { email: 'must be an e-mail' } },
    })
    expect(problemOf(error)).toMatchObject({
      detail: 'Validation failed', code: 'validation', errors: { email: 'must be an e-mail' },
    })
  })

  it('falls back to the error message when the body is not a problem document', () => {
    expect(problemOf(new Error('Network down')).detail).toBe('Network down')
    expect(problemOf(undefined).detail).toBe('Unexpected error')
  })
})
