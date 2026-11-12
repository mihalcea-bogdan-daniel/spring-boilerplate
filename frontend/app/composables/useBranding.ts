import { updatePrimaryPalette } from '@primeuix/themes'

export interface Branding {
  displayName: string | null
  primaryColor: string
  logoUrl: string | null
  allowSelfRegistration: boolean
  allowSocialLogin: boolean
}

const DEFAULTS: Branding = {
  displayName: null, primaryColor: 'brand', logoUrl: null, allowSelfRegistration: true, allowSocialLogin: true,
}

/**
 * Per-tenant look and feel from GET /api/auth/branding (public). Loaded once per page load by
 * plugins/branding.ts; the settings page calls load(true) after saving.
 */
export function useBranding() {
  const branding = useState<Branding>('branding', () => ({ ...DEFAULTS }))
  const loaded = useState('branding-loaded', () => false)
  const nuxtApp = useNuxtApp()
  const appName = useRuntimeConfig().public.appName

  /** Workspace name if the tenant set one, otherwise the product name. */
  const name = computed(() => branding.value.displayName || appName)

  async function load(force = false) {
    if (loaded.value && !force) return
    try {
      branding.value = { ...DEFAULTS, ...(await nuxtApp.$api<Branding>('/auth/branding')) }
    } catch {
      branding.value = { ...DEFAULTS } // API down or unknown tenant: fall back to the product defaults
    }
    loaded.value = true
    applyPalette()
  }

  /**
   * Client only: the PrimeVue theme object is a module-level singleton, so changing it during SSR would leak
   * one tenant's colour into another tenant's response.
   */
  function applyPalette() {
    if (!import.meta.client) return
    const c = branding.value.primaryColor
    const shades = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950]
    updatePrimaryPalette(Object.fromEntries(shades.map(s => [s, `{${c}.${s}}`])))
  }

  return { branding, name, load, applyPalette }
}
