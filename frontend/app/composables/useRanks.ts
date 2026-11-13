import type { RankDto } from '~/types/api'

/**
 * The tenant's rank catalogue, shared by every component on the page (one request), with the name in the
 * current language: labels[locale], else English, else the key.
 */
export function useRanks() {
  const { locale } = useI18n()
  const { data, pending, error, refresh } = useApi<RankDto[]>('/org/ranks', { key: 'org-ranks', server: false })
  const ranks = computed(() => data.value ?? [])
  const byId = computed(() => new Map(ranks.value.map(r => [r.id, r])))

  function label(rank: RankDto | string | null | undefined): string {
    const r = typeof rank === 'string' ? byId.value.get(rank) : rank
    if (!r) return ''
    return r.labels[locale.value] || r.labels.en || r.key
  }

  return { ranks, byId, label, pending, error, refresh }
}
