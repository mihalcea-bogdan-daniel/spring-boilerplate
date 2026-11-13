<script setup lang="ts">
/** Language picker. The choice is stored in the i18n_locale cookie, so SSR renders the next page in it too. */
const { t, locale, locales, setLocale } = useI18n()
const options = computed(() => locales.value.map(l => ({ label: l.name ?? l.code, value: l.code })))
</script>

<template>
  <Select
    :model-value="locale"
    :options="options"
    option-label="label"
    option-value="value"
    :aria-label="t('common.language')"
    size="small"
    @update:model-value="setLocale"
  >
    <template #value="{ value }">
      <span class="row" style="gap: .4rem"><i class="pi pi-globe" />{{ value.toUpperCase() }}</span>
    </template>
  </Select>
</template>
