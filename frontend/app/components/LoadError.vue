<script setup lang="ts">
/**
 * Error state for data loaded with useApi(). Use it next to every list or form so a failed request is never
 * mistaken for "no data":  <LoadError :error="error" @retry="refresh()" />
 */
const props = defineProps<{ error: unknown, what?: string }>()
defineEmits<{ retry: [] }>()

const problem = computed(() => (props.error ? problemOf(props.error) : null))
</script>

<template>
  <Message v-if="problem" severity="error" :closable="false">
    <div class="row-between">
      <span>
        Could not load {{ what ?? 'data' }}: {{ problem.detail }}
        <span v-if="problem.requestId" class="muted">(request {{ problem.requestId }})</span>
      </span>
      <Button label="Retry" icon="pi pi-refresh" size="small" severity="danger" outlined @click="$emit('retry')" />
    </div>
  </Message>
</template>
