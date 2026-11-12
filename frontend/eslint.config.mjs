// @ts-check
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt(
  { ignores: ['app/types/api.generated.ts'] },
  {
    rules: {
      // PrimeVue components are used as <Button />, <Card>...: single-word names are intentional
      'vue/multi-word-component-names': 'off',
    },
  },
)
