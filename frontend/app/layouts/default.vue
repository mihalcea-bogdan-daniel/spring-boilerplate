<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem'
import type { PageResponse, TenantDto } from '~/types/api'

const auth = useAuthStore()
const tenant = useTenantStore()
const { $api } = useNuxtApp()
const { name: appName, branding } = useBranding()
const sidebarOpen = ref(false)
const route = useRoute()
watch(() => route.fullPath, () => { sidebarOpen.value = false })

const menu = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', route: '/' },
  ]
  const admin: MenuItem[] = [
    { label: 'Users', icon: 'pi pi-users', route: '/admin/users', visible: auth.can('user:read') },
    { label: 'Roles', icon: 'pi pi-shield', route: '/admin/roles', visible: auth.can('role:read') },
    { label: 'Settings', icon: 'pi pi-cog', route: '/admin/settings', visible: auth.can('settings:read') },
    { label: 'Audit log', icon: 'pi pi-history', route: '/admin/audit', visible: auth.can('audit:read') },
  ]
  if (admin.some(i => i.visible)) items.push({ label: 'Administration', items: admin })
  if (auth.isSuperAdmin) {
    items.push({ label: 'Platform', items: [{ label: 'Tenants', icon: 'pi pi-building', route: '/platform/tenants' }] })
  }
  return items
})

// ------- super-admin tenant switcher (drives X-Tenant-ID)
const tenantOptions = ref<{ label: string, value: string | null }[]>([])
async function loadTenants() {
  if (!auth.isSuperAdmin) return
  const page = await $api<PageResponse<TenantDto>>('/platform/tenants', { query: { size: 200 } })
  tenantOptions.value = [
    { label: 'Platform (own)', value: null },
    ...page.content.filter(t => t.slug !== auth.user?.tenant).map(t => ({ label: `${t.name} (${t.slug})`, value: t.slug })),
  ]
}
onMounted(loadTenants)
function onTenantChange(slug: string | null) {
  tenant.manage(slug)
  refreshNuxtData() // re-run every useApi() on the page against the new tenant
}

// ------- user menu
const userMenu = ref()
const userItems: MenuItem[] = [
  { label: 'Profile', icon: 'pi pi-user', command: () => navigateTo('/profile') },
  { label: 'Sign out', icon: 'pi pi-sign-out', command: () => auth.logout() },
]

// ------- dark mode (persisted in a cookie, applied in app.vue)
const { isDark: dark, toggle: toggleDark } = useTheme()
</script>

<template>
  <div class="shell">
    <aside class="shell-sidebar" :class="{ open: sidebarOpen }">
      <div class="shell-brand">
        <img v-if="branding.logoUrl" :src="branding.logoUrl" alt="" style="height: 1.5rem">
        <i v-else class="pi pi-box" style="color: var(--p-primary-color)" /> {{ appName }}
      </div>
      <Menu :model="menu">
        <template #item="{ item, props }">
          <NuxtLink v-if="item.route" v-slot="{ href, navigate }" :to="item.route" custom>
            <a :href="href ?? undefined" v-bind="props.action" @click="navigate">
              <span :class="item.icon" />
              <span class="ml-2">{{ item.label }}</span>
            </a>
          </NuxtLink>
        </template>
      </Menu>
    </aside>

    <div class="shell-main">
      <header class="shell-topbar">
        <div class="row">
          <Button class="only-mobile" icon="pi pi-bars" text rounded aria-label="Menu" @click="sidebarOpen = !sidebarOpen" />
          <template v-if="auth.isSuperAdmin">
            <span class="muted">Managing</span>
            <Select
              :model-value="tenant.managedTenant ?? undefined"
              :options="tenantOptions"
              option-label="label"
              option-value="value"
              placeholder="Platform (own)"
              filter
              style="min-width: 14rem"
              @update:model-value="onTenantChange"
            />
            <Tag v-if="tenant.managedTenant" severity="warn" :value="`X-Tenant-ID: ${tenant.managedTenant}`" />
          </template>
          <Tag v-else :value="auth.user?.tenant" />
        </div>
        <div class="row">
          <Button :icon="dark ? 'pi pi-sun' : 'pi pi-moon'" text rounded aria-label="Toggle theme" @click="toggleDark" />
          <Button text @click="userMenu.toggle($event)">
            <Avatar :label="auth.displayName.charAt(0).toUpperCase()" shape="circle" />
            <span>{{ auth.displayName }}</span>
            <i class="pi pi-angle-down" />
          </Button>
          <Menu ref="userMenu" :model="userItems" popup />
        </div>
      </header>
      <Message v-if="auth.user?.impersonatedBy" severity="warn" :closable="false" style="margin: 1rem 1.5rem 0">
        <div class="row-between">
          <span>You are signed in as <strong>{{ auth.user.email }}</strong> on behalf of {{ auth.user.impersonatedBy }}. Everything you do is audited.</span>
          <Button label="End session" size="small" severity="warn" @click="auth.logout()" />
        </div>
      </Message>
      <main class="shell-content">
        <slot />
      </main>
    </div>
  </div>
</template>
