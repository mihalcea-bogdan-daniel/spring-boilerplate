import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'

/** Brand preset: Aura with an emerald primary. Swap the palette here to re-theme every PrimeVue component. */
const AppPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{emerald.50}', 100: '{emerald.100}', 200: '{emerald.200}', 300: '{emerald.300}',
      400: '{emerald.400}', 500: '{emerald.500}', 600: '{emerald.600}', 700: '{emerald.700}',
      800: '{emerald.800}', 900: '{emerald.900}', 950: '{emerald.950}',
    },
  },
})

export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  modules: ['@pinia/nuxt', '@primevue/nuxt-module'],

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
