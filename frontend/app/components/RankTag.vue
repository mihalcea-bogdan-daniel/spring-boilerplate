<script setup lang="ts">
/** A rank's name in the current language, with its level in a tooltip. Pass a rank id or a RankDto. */
import type { RankDto } from '~/types/api'

const props = defineProps<{ rank: RankDto | string | null | undefined }>()
const { label, byId } = useRanks()
const { t } = useI18n()
const resolved = computed(() => (typeof props.rank === 'string' ? byId.value.get(props.rank) : props.rank))
</script>

<template>
  <Tag
    v-if="resolved"
    v-tooltip.top="t('org.levelTip', { level: resolved.level })"
    :value="label(resolved)"
    :severity="resolved.supervises ? 'contrast' : 'secondary'"
  />
</template>
