import Aura from '@primeuix/themes/aura'
import { definePreset } from '@primeuix/themes'
import { brand, ink } from './brand'

const shades = [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950] as const
const paletteRef = (palette: string) => Object.fromEntries(shades.map(s => [s, `{${palette}.${s}}`]))

/**
 * Aura with the brand accent as primary and the warm "ink" greys as surfaces.
 *
 * `brand` and `ink` are registered as primitive palettes, so a tenant can still switch the primary colour to
 * any Aura palette (blue, rose…) from Settings, and switch back to `brand`.
 *
 * Text on the accent is ink.950, not white: white on #dc7e3d is 3:1 and fails WCAG AA, ink is ~7:1.
 */
export const AppPreset = definePreset(Aura, {
  primitive: { brand, ink },
  semantic: {
    primary: paletteRef('brand'),
    colorScheme: {
      light: {
        surface: ink,
        primary: { color: '{primary.500}', contrastColor: ink[950], hoverColor: '{primary.600}', activeColor: '{primary.700}' },
        highlight: { background: '{primary.50}', focusBackground: '{primary.100}', color: '{primary.800}', focusColor: '{primary.900}' },
      },
      dark: {
        surface: ink,
        primary: { color: '{primary.400}', contrastColor: ink[950], hoverColor: '{primary.300}', activeColor: '{primary.200}' },
        highlight: { background: 'color-mix(in srgb, {primary.400}, transparent 84%)', focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)', color: 'rgba(255,255,255,.87)', focusColor: 'rgba(255,255,255,.87)' },
      },
    },
  },
})
