/**
 * Brand palette: the only place hex colours live. Pages use PrimeVue tokens (var(--p-primary-color),
 * var(--p-surface-100), …) which resolve to these values.
 *
 * Accent  #dc7e3d  (brand.500)  buttons, links, focus, highlights
 * Neutrals #d9dadb (ink.200) · #c2bfb4 (ink.300) · #5d5d5a (ink.600) · #171817 (ink.950)
 *
 * The scales are filled in around those anchors so every PrimeVue shade has a value.
 */
export const brand = {
  50: '#fdf5ef',
  100: '#fae8da',
  200: '#f4cdb2',
  300: '#edac83',
  400: '#e58f5c',
  500: '#dc7e3d',
  600: '#c3652a',
  700: '#a24f24',
  800: '#834124',
  900: '#6b3720',
  950: '#3a1a0e',
} as const

export const ink = {
  0: '#ffffff',
  50: '#f6f6f5',
  100: '#ececea',
  200: '#d9dadb',
  300: '#c2bfb4',
  400: '#9b998f',
  500: '#7a7972',
  600: '#5d5d5a',
  700: '#454543',
  800: '#2e2f2d',
  900: '#222321',
  950: '#171817',
} as const

/** Used by the PWA manifest and <meta name="theme-color">. */
export const themeColor = { light: ink[50], dark: ink[950], accent: brand[500] } as const
