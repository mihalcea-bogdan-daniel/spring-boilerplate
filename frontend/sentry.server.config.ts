import * as Sentry from '@sentry/nuxt'
import { sentryDataCollection } from './app/utils/sentry-privacy'

// Server-side rendering errors. Bundled into the Nitro server and started with it (no --import needed).
const dsn = process.env.NUXT_PUBLIC_SENTRY_DSN

Sentry.init({
  dsn: dsn || undefined,
  enabled: !!dsn,
  environment: process.env.NUXT_PUBLIC_SENTRY_ENVIRONMENT ?? 'production',
  release: process.env.NUXT_PUBLIC_SENTRY_RELEASE || undefined,
  dataCollection: sentryDataCollection,
  tracesSampleRate: Number(process.env.NUXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE) || 0,
})
