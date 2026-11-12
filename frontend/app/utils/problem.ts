import type { FetchError } from 'ofetch'
import type { Problem } from '~/types/api'

/** Extracts a human message (and field errors) from an ofetch error carrying a problem+json body. */
export function problemOf(error: unknown): Required<Pick<Problem, 'detail'>> & Problem {
  const data = (error as FetchError<Problem> | undefined)?.data
  return {
    ...data,
    detail: data?.detail ?? (error instanceof Error ? error.message : 'Unexpected error'),
  }
}
