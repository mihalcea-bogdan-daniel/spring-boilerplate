/**
 * Light/dark preference stored in a cookie so the server renders the right theme (no flash on reload).
 * PrimeVue's darkModeSelector is `.app-dark` on <html> (see nuxt.config.ts).
 */
export function useTheme() {
  const theme = useCookie<'light' | 'dark'>('theme', {
    sameSite: 'lax',
    maxAge: 60 * 60 * 24 * 365,
    default: () => 'light',
  })
  const isDark = computed(() => theme.value === 'dark')

  function toggle() {
    theme.value = isDark.value ? 'light' : 'dark'
  }

  return { theme, isDark, toggle }
}
