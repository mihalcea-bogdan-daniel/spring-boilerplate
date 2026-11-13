<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem'
import type { PageResponse, TenantDto } from '~/types/api'

const auth = useAuthStore()
const { t } = useI18n()
const tenant = useTenantStore()
const { $api } = useNuxtApp()
const { name: appName, branding } = useBranding()
const sidebarOpen = ref(false)
const route = useRoute()
watch(() => route.fullPath, () => { sidebarOpen.value = false })

const menu = computed<MenuItem[]>(() => {
  const items: MenuItem[] = [
    { label: t('layout.dashboard'), icon: 'pi pi-home', route: '/' },
    { label: t('layout.organization'), icon: 'pi pi-sitemap', route: '/organization', visible: auth.can('org:read') },
    { label: t('layout.team'), icon: 'pi pi-users', route: '/team', visible: auth.can('org:read') },
    { label: t('layout.projects'), icon: 'pi pi-folder', route: '/projects', visible: auth.can('project:read') },
    { label: t('layout.groups'), icon: 'pi pi-th-large', route: '/groups', visible: auth.can('org:read') },
  ]
  const admin: MenuItem[] = [
    { label: t('layout.users'), icon: 'pi pi-users', route: '/admin/users', visible: auth.can('user:read') },
    { label: t('layout.roles'), icon: 'pi pi-shield', route: '/admin/roles', visible: auth.can('role:read') },
    { label: t('layout.settings'), icon: 'pi pi-cog', route: '/admin/settings', visible: auth.can('settings:read') },
    { label: t('layout.apiKeys'), icon: 'pi pi-key', route: '/admin/api-keys', visible: auth.can('apikey:manage') },
    { label: t('layout.audit'), icon: 'pi pi-history', route: '/admin/audit', visible: auth.can('audit:read') },
    { label: t('layout.ranks'), icon: 'pi pi-sort-amount-down', route: '/admin/ranks', visible: auth.can('org:manage') },
  ]
  if (admin.some(i => i.visible)) items.push({ label: t('layout.administration'), items: admin })
  if (auth.isSuperAdmin) {
    items.push({ label: t('layout.platform'), items: [{ label: t('layout.tenants'), icon: 'pi pi-building', route: '/platform/tenants' }] })
  }
  return items
})

// ------- super-admin tenant switcher (drives X-Tenant-ID)
const otherTenants = ref<TenantDto[]>([])
const tenantOptions = computed(() => [
  { label: t('layout.platformOwn'), value: null },
  ...otherTenants.value.map(x => ({ label: `${x.name} (${x.slug})`, value: x.slug })),
])
async function loadTenants() {
  if (!auth.isSuperAdmin) return
  const page = await $api<PageResponse<TenantDto>>('/platform/tenants', { query: { size: 200 } })
  otherTenants.value = page.content.filter(x => x.slug !== auth.user?.tenant)
}
onMounted(loadTenants)
function onTenantChange(slug: string | null) {
  tenant.manage(slug)
  refreshNuxtData() // re-run every useApi() on the page against the new tenant
}

// ------- user menu
const userMenu = ref()
const { $pwa } = useNuxtApp()
const userItems = computed<MenuItem[]>(() => [
  { label: t('layout.profile'), icon: 'pi pi-user', command: () => navigateTo('/profile') },
  // shown only when the browser offers installation and the app isn't installed yet
  { label: t('layout.installApp'), icon: 'pi pi-download', visible: !!$pwa?.showInstallPrompt, command: () => $pwa?.install() },
  { label: t('layout.signOut'), icon: 'pi pi-sign-out', command: () => auth.logout() },
])

// ------- dark mode (persisted in a cookie, applied in app.vue)
const { isDark: dark, toggle: toggleDark } = useTheme()
</script>

<template>
  <div class="shell">
    <aside class="shell-sidebar" :class="{ open: sidebarOpen }">
      <div class="shell-brand">
        <img v-if="branding.logoUrl" :src="branding.logoUrl" alt="" style="height: 1.5rem">
        <AppLogo v-else size="1.75rem" /> {{ appName }}
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
          <Button class="only-mobile" icon="pi pi-bars" text rounded :aria-label="t('layout.menu')" @click="sidebarOpen = !sidebarOpen" />
          <template v-if="auth.isSuperAdmin">
            <span class="muted">{{ t('layout.managing') }}</span>
            <Select
              :model-value="tenant.managedTenant ?? undefined"
              :options="tenantOptions"
              option-label="label"
              option-value="value"
              :placeholder="t('layout.platformOwn')"
              filter
              style="min-width: 14rem"
              @update:model-value="onTenantChange"
            />
            <Tag v-if="tenant.managedTenant" severity="warn" :value="`X-Tenant-ID: ${tenant.managedTenant}`" />
          </template>
          <Tag v-else :value="auth.user?.tenant" />
        </div>
        <div class="row">
          <LocaleSwitcher />
          <Button :icon="dark ? 'pi pi-sun' : 'pi pi-moon'" text rounded :aria-label="t('layout.toggleTheme')" @click="toggleDark" />
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
          <i18n-t keypath="layout.impersonating" tag="span">
            <template #email><strong>{{ auth.user.email }}</strong></template>
            <template #by>{{ auth.user.impersonatedBy }}</template>
          </i18n-t>
          <Button :label="t('layout.endSession')" size="small" severity="warn" @click="auth.logout()" />
        </div>
      </Message>
      <main class="shell-content">
        <slot />
      </main>
    </div>
  </div>
</template>
