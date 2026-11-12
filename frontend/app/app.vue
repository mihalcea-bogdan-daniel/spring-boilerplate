<script setup lang="ts">
const { isDark } = useTheme()
useHead({ htmlAttrs: { class: computed(() => (isDark.value ? 'app-dark' : '')) } })
</script>

<template>
  <NuxtPwaManifest />
  <NuxtLoadingIndicator color="var(--p-primary-color)" />
  <Toast position="top-right" />
  <ConfirmDialog />
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>
