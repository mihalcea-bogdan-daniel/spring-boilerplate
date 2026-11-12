<script setup lang="ts">
const auth = useAuthStore()
const tenant = useTenantStore()
const { t } = useI18n()
</script>

<template>
  <div class="stack">
    <h1 class="page-title">{{ t('home.welcome', { name: auth.displayName }) }}</h1>
    <div class="grid-2">
      <Card>
        <template #title>{{ t('home.workspace') }}</template>
        <template #content>
          <p><strong>{{ auth.user?.tenant }}</strong></p>
          <p v-if="tenant.managedTenant" class="muted">{{ t('home.managing') }} <Tag severity="warn" :value="tenant.managedTenant" /></p>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('common.roles') }}</template>
        <template #content>
          <div class="row" style="flex-wrap: wrap">
            <Tag v-for="r in auth.user?.roles" :key="r" :value="r" />
          </div>
        </template>
      </Card>
      <Card>
        <template #title>{{ t('common.permissions') }}</template>
        <template #content>
          <div class="row" style="flex-wrap: wrap">
            <Chip v-for="p in auth.user?.permissions" :key="p" :label="p" />
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>
