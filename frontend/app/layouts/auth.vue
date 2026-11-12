<script setup lang="ts">
const { name: appName, branding } = useBranding()
const host = useRequestURL().hostname
</script>

<template>
  <div class="center-screen">
    <div class="stack" style="width: 100%; max-width: 26rem">
      <div class="row" style="justify-content: center">
        <img v-if="branding.logoUrl" :src="branding.logoUrl" alt="" style="height: 2rem">
        <i v-else class="pi pi-box" style="font-size: 1.75rem; color: var(--p-primary-color)" />
        <strong style="font-size: 1.4rem">{{ appName }}</strong>
      </div>
      <slot />
      <p class="muted" style="text-align: center">Workspace: <code>{{ host }}</code></p>
    </div>
  </div>
</template>
