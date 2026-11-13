<script setup lang="ts">
/**
 * Error state for data loaded with useApi(). Use it next to every list or form so a failed request is never
 * mistaken for "no data":  <LoadError :error="error" :what="t('users.what')" @retry="refresh()" />
 * `what` is an already translated noun phrase.
 */
const props = defineProps<{ error: unknown, what?: string }>()
defineEmits<{ retry: [] }>()
const { t } = useI18n()

const problem = computed(() => (props.error ? problemOf(props.error) : null))
</script>

<template>
  <Message v-if="problem" severity="error" :closable="false">
    <div class="row-between">
      <span>
        {{ t('loadError.message', { what: what ?? t('loadError.data'), detail: problem.detail }) }}
        <span v-if="problem.requestId" class="muted">{{ t('loadError.request', { id: problem.requestId }) }}</span>
      </span>
      <Button :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="danger" outlined @click="$emit('retry')" />
    </div>
  </Message>
</template>
