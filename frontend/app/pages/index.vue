<script setup lang="ts">
const auth = useAuthStore()
const tenant = useTenantStore()
</script>

<template>
  <div class="stack">
    <h1 class="page-title">Welcome, {{ auth.displayName }}</h1>
    <div class="grid-2">
      <Card>
        <template #title>Workspace</template>
        <template #content>
          <p><strong>{{ auth.user?.tenant }}</strong></p>
          <p v-if="tenant.managedTenant" class="muted">Currently managing: <Tag severity="warn" :value="tenant.managedTenant" /></p>
        </template>
      </Card>
      <Card>
        <template #title>Roles</template>
        <template #content>
          <div class="row" style="flex-wrap: wrap">
            <Tag v-for="r in auth.user?.roles" :key="r" :value="r" />
          </div>
        </template>
      </Card>
      <Card>
        <template #title>Permissions</template>
        <template #content>
          <div class="row" style="flex-wrap: wrap">
            <Chip v-for="p in auth.user?.permissions" :key="p" :label="p" />
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>
