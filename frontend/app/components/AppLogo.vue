<script setup lang="ts">
/** Product mark (public/logo.svg). Tenants with their own logo URL show that instead; see layouts. */
withDefaults(defineProps<{ size?: string }>(), { size: '1.5rem' })
</script>

<template>
  <img src="/logo.svg" alt="" :style="{ width: size, height: size, borderRadius: '22%' }">
</template>
