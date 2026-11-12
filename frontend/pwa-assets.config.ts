import { defineConfig, minimal2023Preset } from '@vite-pwa/assets-generator/config'

// `npx pwa-assets-generator` regenerates the icons in public/ from public/logo.svg.
// The maskable icon gets the dark brand background so Android's circle/squircle crop stays on-brand.
export default defineConfig({
  headLinkOptions: { preset: '2023' },
  preset: {
    ...minimal2023Preset,
    maskable: { ...minimal2023Preset.maskable, resizeOptions: { background: '#171817' } },
    apple: { ...minimal2023Preset.apple, resizeOptions: { background: '#171817' } },
  },
  images: ['public/logo.svg'],
})
