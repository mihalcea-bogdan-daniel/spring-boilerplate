import type { DataCollection } from '@sentry/core'

/**
 * Sentry v11 collects request data by default. This app handles tokens, OTP codes and personal data, so collect
 * nothing beyond the error itself: no user auto-detection, cookies, headers, bodies, query strings or locals.
 * The user is identified by id only (plugins/sentry-context.client.ts).
 */
export const sentryDataCollection: DataCollection = {
  userInfo: false,
  cookies: false,
  httpHeaders: false,
  httpBodies: [],
  urlQueryParams: false, // e.g. /auth/callback?code=..., /auth/otp?email=...
  databaseQueryData: false,
  queues: false,
}
