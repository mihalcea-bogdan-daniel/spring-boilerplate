<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()
const { t } = useI18n()
</script>

<template>
  <div class="center-screen">
    <Card class="auth-card">
      <template #title>{{ props.error.statusCode }}</template>
      <template #content>
        <p>{{ props.error.statusMessage || props.error.message }}</p>
      </template>
      <template #footer>
        <Button :label="t('error.backHome')" icon="pi pi-home" @click="clearError({ redirect: '/' })" />
      </template>
    </Card>
  </div>
</template>
