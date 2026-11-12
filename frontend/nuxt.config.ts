import { AppPreset } from './app/theme/preset'
import { themeColor } from './app/theme/brand'

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: ['@pinia/nuxt', '@primevue/nuxt-module', '@nuxt/eslint', '@sentry/nuxt/module', '@vite-pwa/nuxt', '@nuxtjs/i18n'],

  app: {
    head: {
      link: [
        { rel: 'icon', href: '/favicon.ico', sizes: '48x48' },
        { rel: 'icon', href: '/logo.svg', type: 'image/svg+xml', sizes: 'any' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon-180x180.png' },
      ],
      meta: [
        { name: 'theme-color', content: themeColor.light, media: '(prefers-color-scheme: light)' },
        { name: 'theme-color', content: themeColor.dark, media: '(prefers-color-scheme: dark)' },
      ],
    },
  },

  // Every UI string comes from i18n/locales/<code>.json (see AGENTS.md). URLs carry no locale prefix: the
  // choice is a cookie, first set from the browser's Accept-Language. To add a language, add a line here,
  // a JSON file next to en.json and, if needed, PrimeVue texts in i18n/primevue.ts.
  i18n: {
    strategy: 'no_prefix',
    defaultLocale: 'en',
    locales: [
      { code: 'en', language: 'en-US', name: 'English', file: 'en.json' },
      { code: 'ro', language: 'ro-RO', name: 'Română', file: 'ro.json' },
    ],
    detectBrowserLanguage: { useCookie: true, cookieKey: 'i18n_locale', redirectOn: 'root', fallbackLocale: 'en' },
  },

  // Installable app. Icons in public/ are generated from public/logo.svg: `npx pwa-assets-generator`.
  pwa: {
    registerType: 'autoUpdate',
    manifest: {
      name: process.env.NUXT_PUBLIC_APP_NAME ?? 'Boilerplate',
      short_name: process.env.NUXT_PUBLIC_APP_NAME ?? 'Boilerplate',
      description: 'Multi-tenant workspace',
      theme_color: themeColor.dark,
      background_color: themeColor.dark,
      display: 'standalone',
      start_url: '/',
      scope: '/',
      icons: [
        { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
        { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
        { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
    workbox: {
      // Pages are server-rendered per tenant and per user: never serve them (or the API) from the cache.
      // Only the hashed build assets and icons are precached, so repeat visits and the installed app start fast.
      navigateFallback: null,
      globPatterns: ['_nuxt/**/*.{js,css,woff2}', '*.{png,svg,ico}'],
      navigateFallbackDenylist: [/^\/api\//],
      cleanupOutdatedCaches: true,
    },
    client: { installPrompt: true },
    devOptions: { enabled: false },
  },

  // Build-time Sentry options: source maps are uploaded only when SENTRY_AUTH_TOKEN is present in CI.
  sentry: {
    org: process.env.SENTRY_ORG,
    project: process.env.SENTRY_PROJECT,
    authToken: process.env.SENTRY_AUTH_TOKEN,
    sourcemaps: { disable: !process.env.SENTRY_AUTH_TOKEN },
    telemetry: false,
  },

  // Hidden source maps: generated for Sentry, not referenced from the served files.
  sourcemap: { client: process.env.SENTRY_AUTH_TOKEN ? 'hidden' : false },

  css: ['primeicons/primeicons.css', '~/assets/css/main.css'],

  typescript: {
    strict: true,
    typeCheck: false, // run `npm run typecheck` in CI instead of slowing down dev
  },

  primevue: {
    options: {
      ripple: true,
      theme: {
        preset: AppPreset,
        options: { darkModeSelector: '.app-dark', cssLayer: false },
      },
    },
  },

  runtimeConfig: {
    // Server-side (SSR) calls go straight to the backend on the internal network.
    // NUXT_API_INTERNAL_BASE=http://backend:8080/api
    apiInternalBase: 'http://localhost:8080/api',
    public: {
      // Browser calls stay same-origin; nginx/apache (or the dev proxy below) forwards /api to Spring.
      apiBase: '/api',
      tenantHeader: 'X-Tenant-ID',
      appName: 'Boilerplate',
      oauth2Providers: ['google', 'github'],
      // NUXT_PUBLIC_SENTRY_DSN etc.; empty DSN = Sentry off
      sentry: { dsn: '', environment: 'production', release: '', tracesSampleRate: 0 },
    },
  },

  routeRules: {
    // The dashboard needs the in-memory session; render it client-side. Auth pages stay SSR.
    '/admin/**': { ssr: false },
    '/platform/**': { ssr: false },
  },

  nitro: {
    // Dev only: emulate nginx. changeOrigin=false keeps Host (e.g. acme.localhost:3000) => backend resolves tenant.
    devProxy: {
      '/api': { target: 'http://localhost:8080/api', changeOrigin: false },
    },
  },
})
