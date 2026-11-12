import * as Sentry from '@sentry/nuxt'
import { sentryDataCollection } from './app/utils/sentry-privacy'

// Browser error reporting. Inactive unless NUXT_PUBLIC_SENTRY_DSN is set.
const { sentry } = useRuntimeConfig().public

Sentry.init({
  dsn: sentry.dsn || undefined,
  enabled: !!sentry.dsn,
  environment: sentry.environment,
  release: sentry.release || undefined,
  dataCollection: sentryDataCollection,
  tracesSampleRate: Number(sentry.tracesSampleRate) || 0,
  // Expected API problems (401 → refresh, 4xx validation) are handled in the UI and are not bugs
  ignoreErrors: [/\[(GET|POST|PUT|DELETE)\] .* (400|401|403|404|409|422|429) /],
})
