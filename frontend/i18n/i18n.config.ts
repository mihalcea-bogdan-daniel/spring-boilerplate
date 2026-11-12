// Same shapes for every locale; Intl formats them in the locale's own order and language.
const datetime = {
  date: { year: 'numeric', month: 'short', day: 'numeric' },
  datetime: { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' },
} as const

/** Runtime options for vue-i18n. Messages live in ./locales/*.json and are loaded lazily per locale. */
export default defineI18nConfig(() => ({
  fallbackLocale: 'en',
  datetimeFormats: { en: datetime, ro: datetime },
}))
