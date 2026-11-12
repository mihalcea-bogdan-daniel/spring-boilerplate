/** Loads the tenant's branding during SSR (state is transferred to the client) and applies the colour. */
export default defineNuxtPlugin({
  name: 'branding',
  dependsOn: ['api'],
  async setup(nuxtApp) {
    const { load, applyPalette } = useBranding()
    await load()
    nuxtApp.hook('app:mounted', applyPalette)
  },
})
