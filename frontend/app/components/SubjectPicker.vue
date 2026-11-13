<script setup lang="ts">
/**
 * Search people, units and groups (GET /api/access/subjects). `kinds` limits what is offered.
 *   <SubjectPicker v-model="subject" :kinds="['USER']" />
 */
import type { SubjectDto, SubjectKind } from '~/types/api'

const props = withDefaults(defineProps<{ kinds?: SubjectKind[], placeholder?: string }>(), {
  kinds: () => ['USER', 'UNIT', 'GROUP'],
  placeholder: undefined,
})
const model = defineModel<SubjectDto | null>({ default: null })
const { $api } = useNuxtApp()
const { t } = useI18n()
const suggestions = ref<SubjectDto[]>([])

async function search(e: { query: string }) {
  try {
    const found = await $api<SubjectDto[]>('/access/subjects', { query: { q: e.query } })
    suggestions.value = found.filter(s => props.kinds.includes(s.kind))
  } catch {
    suggestions.value = []
  }
}

const icon = (kind: SubjectKind) => ({ USER: 'pi pi-user', UNIT: 'pi pi-sitemap', GROUP: 'pi pi-users' }[kind])
</script>

<template>
  <AutoComplete
    v-model="model"
    :suggestions="suggestions"
    option-label="name"
    :placeholder="placeholder ?? t('access.searchSubjects')"
    force-selection
    dropdown
    fluid
    @complete="search"
  >
    <template #option="{ option }">
      <span class="row" style="gap: .5rem">
        <i :class="icon(option.kind)" />
        <span>{{ option.name }}</span>
        <span v-if="option.detail" class="muted">{{ option.detail }}</span>
      </span>
    </template>
  </AutoComplete>
</template>
