import { usePrimeVue, type PrimeVueLocaleOptions } from 'primevue/config'
import { primeVueLocales } from '../../i18n/primevue'

/**
 * Keeps <html lang> and PrimeVue's built-in texts (paginator, filters, password strength) in step with the
 * vue-i18n locale. Called once, in app.vue.
 */
export function useLocaleSync() {
  const { locale } = useI18n()
  const primevue = usePrimeVue()
  const english = { ...primevue.config.locale } // PrimeVue's defaults, restored when switching back

  watch(locale, (code) => {
    primevue.config.locale = { ...english, ...primeVueLocales[code] } as PrimeVueLocaleOptions
  }, { immediate: true })

  useHead({ htmlAttrs: { lang: locale } })
}
