# Theming and PWA

## Palette

Defined once in [`app/theme/brand.ts`](../app/theme/brand.ts) and turned into a PrimeVue preset in
[`app/theme/preset.ts`](../app/theme/preset.ts).

| Role | Token | Value |
| --- | --- | --- |
| Accent (buttons, links, focus) | `brand.500` → `--p-primary-color` (light) | `#dc7e3d` |
| Accent in dark mode | `brand.400` | `#e58f5c` |
| Text on accent | `--p-primary-contrast-color` | `#171817` |
| Light page background | `ink.50` → `--p-surface-50` | `#f6f6f5` |
| Light borders, dividers | `ink.200` | `#d9dadb` |
| Warm secondary surface | `ink.300` | `#c2bfb4` |
| Secondary text | `ink.600` | `#5d5d5a` |
| Dark page background / ink | `ink.950` → `--p-surface-950` | `#171817` |

Text on the accent is dark, not white: white on `#dc7e3d` is about 3:1 and fails WCAG AA; `#171817` is about 7:1.

### Rules

- Pages never contain hex colours. Use `var(--p-primary-color)`, `var(--p-surface-100)`,
  `var(--p-text-color)`, `var(--p-text-muted-color)`, `var(--p-content-border-color)`.
- Severity colours (`success`, `warn`, `danger`, `info`) come from PrimeVue `severity` props, never custom CSS.
- To re-brand, edit `brand.ts` only. Keep `ink.950` and the accent's 500 shade, regenerate the icons (below).
- Dark mode is the `.app-dark` class on `<html>` (`useTheme()`); both schemes use the same `ink` scale.

### Per-tenant colour

Admins pick a primary palette in **Settings**. `brand` is the default and the first option; any Aura palette
(`blue`, `rose`…) also works. The choice is applied client-side by `useBranding().applyPalette()`, so SSR never
leaks one tenant's colour into another tenant's page. A tenant logo URL replaces `<AppLogo>`.

## Logo

[`public/logo.svg`](../public/logo.svg): an ink tile with a light hexagon and an accent orbit that passes behind
it at the top and in front at the bottom. `<AppLogo size="2rem" />` renders it.

## PWA

`@vite-pwa/nuxt` makes the dashboard installable (Chrome/Edge address bar, Android "Add to home screen",
iOS Share → Add to Home Screen). Configured in `nuxt.config.ts` under `pwa`.

- **What is cached:** hashed build assets (`/_nuxt/*`) and icons only. HTML pages and `/api` are always fetched
  from the network: they are rendered per tenant and per user, and serving a cached copy would show stale or
  someone else's data. Offline, the app shows the browser's offline page.
- **Updates:** `registerType: 'autoUpdate'`. A new deploy activates on the next navigation.
- **Install button:** the user menu shows "Install app" when the browser offers installation.
- **Name:** the manifest uses `NUXT_PUBLIC_APP_NAME` at build time (default "Boilerplate").
- **Dev:** the service worker is off in `npm run dev`. Test with `npm run build && npm run preview`
  on `localhost` (service workers need HTTPS or localhost).

### Regenerating icons

After changing `public/logo.svg`:

```bash
npx pwa-assets-generator
```

This rewrites `favicon.ico`, `pwa-*.png`, `maskable-icon-512x512.png` and `apple-touch-icon-180x180.png` from
the settings in `pwa-assets.config.ts`. Commit the PNGs.
