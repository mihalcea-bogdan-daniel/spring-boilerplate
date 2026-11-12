<script setup lang="ts">
import type { SessionDto } from '~/types/api'

const auth = useAuthStore()
const toast = useToast()

const profile = reactive({ firstName: auth.user?.firstName ?? '', lastName: auth.user?.lastName ?? '' })
const savingProfile = ref(false)
async function saveProfile() {
  savingProfile.value = true
  try {
    await auth.updateProfile(profile.firstName, profile.lastName)
    toast.add({ severity: 'success', summary: 'Profile updated', life: 3000 })
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  } finally {
    savingProfile.value = false
  }
}

const password = reactive({ current: '', next: '', confirm: '' })
const passwordError = ref<string | null>(null)
const savingPassword = ref(false)
async function savePassword() {
  passwordError.value = null
  if (password.next !== password.confirm) {
    passwordError.value = 'Passwords do not match'
    return
  }
  savingPassword.value = true
  try {
    await auth.changePassword(auth.user?.hasPassword ? password.current : null, password.next)
    toast.add({ severity: 'success', summary: 'Password updated', detail: 'Please sign in again.', life: 4000 })
    await auth.logout()
  } catch (e) {
    const p = problemOf(e)
    passwordError.value = p.errors?.newPassword ?? p.detail
  } finally {
    savingPassword.value = false
  }
}

// ---------------------------------------------------------------- active sessions (refresh tokens)
const { $api } = useNuxtApp()
const { data: sessions, refresh: reloadSessions } = useApi<SessionDto[]>('/me/sessions', { server: false })

async function revokeSession(s: SessionDto) {
  try {
    await $api(`/me/sessions/${s.id}`, { method: 'DELETE' })
    await reloadSessions()
  } catch (e) {
    toast.add({ severity: 'error', summary: problemOf(e).detail, life: 5000 })
  }
}

async function signOutEverywhere() {
  await $api('/me/sessions', { method: 'DELETE' })
  await auth.logout()
}

/** Short human label for a User-Agent string. */
function device(ua: string | null): string {
  if (!ua) return 'Unknown device'
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : ''
  return os ? `${browser} on ${os}` : browser
}
</script>

<template>
  <div class="stack" style="max-width: 40rem">
    <h1 class="page-title">Profile</h1>

    <Card>
      <template #title>Personal details</template>
      <template #subtitle>{{ auth.user?.email }}</template>
      <template #content>
        <form class="stack" @submit.prevent="saveProfile">
          <div class="grid-2">
            <div class="field">
              <label for="p-fn">First name</label>
              <InputText id="p-fn" v-model="profile.firstName" autocomplete="given-name" fluid />
            </div>
            <div class="field">
              <label for="p-ln">Last name</label>
              <InputText id="p-ln" v-model="profile.lastName" autocomplete="family-name" fluid />
            </div>
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" label="Save" icon="pi pi-check" :loading="savingProfile" />
          </div>
        </form>
      </template>
    </Card>

    <Card>
      <template #title>{{ auth.user?.hasPassword ? 'Change password' : 'Set a password' }}</template>
      <template #subtitle>
        {{ auth.user?.hasPassword
          ? 'You will be signed out on every device.'
          : 'You currently sign in with an e-mail code or a social account.' }}
      </template>
      <template #content>
        <form class="stack" @submit.prevent="savePassword">
          <Message v-if="passwordError" severity="error" :closable="false">{{ passwordError }}</Message>
          <div v-if="auth.user?.hasPassword" class="field">
            <label for="p-cur">Current password</label>
            <Password id="p-cur" v-model="password.current" :feedback="false" toggle-mask
                      autocomplete="current-password" required fluid />
          </div>
          <div class="field">
            <label for="p-new">New password</label>
            <Password id="p-new" v-model="password.next" toggle-mask autocomplete="new-password" required fluid />
            <small class="muted">At least 10 characters with letters and digits.</small>
          </div>
          <div class="field">
            <label for="p-conf">Confirm new password</label>
            <Password id="p-conf" v-model="password.confirm" :feedback="false" toggle-mask
                      autocomplete="new-password" required fluid />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" label="Update password" icon="pi pi-lock" :loading="savingPassword" />
          </div>
        </form>
      </template>
    </Card>

    <Card>
      <template #title>Active sessions</template>
      <template #subtitle>Devices that can still refresh their sign-in. A revoked device is signed out within 15 minutes.</template>
      <template #content>
        <DataTable :value="sessions ?? []" data-key="id" size="small">
          <template #empty>No active sessions.</template>
          <Column header="Device">
            <template #body="{ data: s }">{{ device(s.userAgent) }}</template>
          </Column>
          <Column field="ipAddress" header="IP" />
          <Column header="Signed in">
            <template #body="{ data: s }">{{ new Date(s.createdAt).toLocaleString() }}</template>
          </Column>
          <Column style="width: 4rem; text-align: right">
            <template #body="{ data: s }">
              <Button icon="pi pi-times" text rounded severity="danger" aria-label="Revoke" @click="revokeSession(s)" />
            </template>
          </Column>
        </DataTable>
        <div class="row" style="justify-content: flex-end; margin-top: 1rem">
          <Button label="Sign out everywhere" icon="pi pi-sign-out" severity="danger" outlined @click="signOutEverywhere" />
        </div>
      </template>
    </Card>
  </div>
</template>
