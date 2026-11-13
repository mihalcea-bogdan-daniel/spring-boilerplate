import { describe, expect, it } from 'vitest'
import en from '../i18n/locales/en.json'
import ro from '../i18n/locales/ro.json'

/** Flattens { a: { b: 'x' } } into ['a.b']. */
function keys(node: object, prefix = ''): string[] {
  return Object.entries(node).flatMap(([k, v]) =>
    typeof v === 'object' && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`])
}

/** Placeholder names used in a message: 'Hi {name}' -> ['name']. */
const params = (msg: string) => [...msg.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort()

describe('locale files', () => {
  const locales = { ro }

  for (const [code, messages] of Object.entries(locales)) {
    it(`${code} has exactly the keys of en`, () => {
      expect(keys(messages).sort()).toEqual(keys(en).sort())
    })

    it(`${code} uses the same placeholders as en`, () => {
      const get = (o: object, path: string) => path.split('.').reduce<unknown>((n, k) => (n as Record<string, unknown>)[k], o) as string
      for (const key of keys(en)) expect(params(get(messages, key)), key).toEqual(params(get(en, key)))
    })
  }
})
