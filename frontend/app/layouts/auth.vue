<script setup lang="ts">
const { name: appName, branding, missing } = useBranding()
const host = useRequestURL().hostname
const { t } = useI18n()
</script>

<template>
  <div class="center-screen">
    <div class="stack" style="width: 100%; max-width: 26rem">
      <div class="row" style="justify-content: center">
        <img v-if="branding.logoUrl" :src="branding.logoUrl" alt="" style="height: 2rem">
        <AppLogo v-else size="2.25rem" />
        <strong style="font-size: 1.4rem">{{ appName }}</strong>
      </div>
      <Card v-if="missing">
        <template #title>{{ t('workspace.missingTitle') }}</template>
        <template #content>
          <i18n-t keypath="workspace.missingBody" tag="p">
            <template #host><code>{{ host }}</code></template>
          </i18n-t>
        </template>
      </Card>
      <slot v-else />
      <div class="row" style="justify-content: center">
        <span class="muted">{{ t('layout.workspace') }} <code>{{ host }}</code></span>
        <LocaleSwitcher />
      </div>
    </div>
  </div>
</template>
