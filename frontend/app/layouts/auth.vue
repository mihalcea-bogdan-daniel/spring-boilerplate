<script setup lang="ts">
const appName = useRuntimeConfig().public.appName
const host = useRequestURL().hostname
</script>

<template>
  <div class="center-screen">
    <div class="stack" style="width: 100%; max-width: 26rem">
      <div class="row" style="justify-content: center">
        <i class="pi pi-box" style="font-size: 1.75rem; color: var(--p-primary-color)" />
        <strong style="font-size: 1.4rem">{{ appName }}</strong>
      </div>
      <slot />
      <p class="muted" style="text-align: center">Workspace: <code>{{ host }}</code></p>
    </div>
  </div>
</template>
