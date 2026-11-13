// @ts-check
import vueI18n from '@intlify/eslint-plugin-vue-i18n'
import withNuxt from './.nuxt/eslint.config.mjs'

export default withNuxt(
  { ignores: ['app/types/api.generated.ts'] },
  {
    rules: {
      // PrimeVue components are used as <Button />, <Card>...: single-word names are intentional
      'vue/multi-word-component-names': 'off',
    },
  },
  // i18n: every user-visible string in a template comes from i18n/locales/*.json (AGENTS.md rule 13)
  ...vueI18n.configs.recommended,
  {
    settings: {
      'vue-i18n': { localeDir: './i18n/locales/*.json', messageSyntaxVersion: '^11.0.0' },
    },
    rules: {
      '@intlify/vue-i18n/no-raw-text': ['error', {
        attributes: { '/.+/': ['label', 'header', 'placeholder', 'title', 'alt', 'aria-label', 'value', 'summary'] },
        // punctuation, separators and code-like tokens that are the same in every language
        ignorePattern: '^[-—–…·:()/|,.\\s#*&]*$',
        // example values in placeholders show a data format, not prose
        ignoreText: ['IP', '(https)', 'X-API-Key', 'X-Tenant-ID',
          'BILLING_MANAGER', 'xxxxx-xxxxx', 'https://cdn.example.com/logo.svg', 'acme', 'Acme Inc.', 'portal.acme.com'],
      }],
      '@intlify/vue-i18n/no-missing-keys': 'error',
      '@intlify/vue-i18n/no-missing-keys-in-other-locales': 'error',
      '@intlify/vue-i18n/no-html-messages': 'error',
      '@intlify/vue-i18n/no-duplicate-keys-in-locale': 'error',
      '@intlify/vue-i18n/valid-message-syntax': 'error',
      '@intlify/vue-i18n/no-dynamic-keys': 'off', // status.${s} is intentional; the locale-file test checks coverage
      '@intlify/vue-i18n/no-v-html': 'error',
    },
  },
)
