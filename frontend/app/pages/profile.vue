<script setup lang="ts">
import QRCode from 'qrcode'
import type { PasskeyDto, SessionDto } from '~/types/api'

const auth = useAuthStore()
const toast = useToast()
const { request, notifyError } = useRequest()
const { request: mfaRequest, pending: mfaBusy } = useRequest()

const profile = reactive({ firstName: auth.user?.firstName ?? '', lastName: auth.user?.lastName ?? '' })
const savingProfile = ref(false)
async function saveProfile() {
  savingProfile.value = true
  try {
    await auth.updateProfile(profile.firstName, profile.lastName)
    toast.add({ severity: 'success', summary: 'Profile updated', life: 3000 })
  } catch (e) {
    notifyError(e, 'Could not update the profile')
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
const { data: sessions, error: sessionsError, refresh: reloadSessions } = useApi<SessionDto[]>('/me/sessions', { server: false })

async function revokeSession(s: SessionDto) {
  const res = await request(`/me/sessions/${s.id}`, { method: 'DELETE', success: 'Session signed out' })
  if (res.ok) await reloadSessions()
}

async function signOutEverywhere() {
  const res = await request('/me/sessions', { method: 'DELETE', success: false, error: 'Could not sign out other sessions' })
  if (res.ok) await auth.logout()
}

// ---------------------------------------------------------------- two-factor authentication (TOTP)
const mfaSetup = ref<{ secret: string, otpauthUri: string, qr: string } | null>(null)
const mfaCode = ref('')
const recoveryCodes = ref<string[] | null>(null)
const disableDialog = ref(false)

async function beginMfaSetup() {
  const res = await mfaRequest<{ secret: string, otpauthUri: string }>('/me/mfa/setup', { method: 'POST', success: false })
  if (!res.ok) return
  mfaSetup.value = { ...res.data, qr: await QRCode.toDataURL(res.data.otpauthUri, { margin: 1, width: 200 }) }
  mfaCode.value = ''
}

async function enableMfa() {
  const res = await mfaRequest<{ recoveryCodes: string[] }>('/me/mfa/enable', {
    method: 'POST', body: { code: mfaCode.value }, success: 'Two-factor authentication turned on',
  })
  if (!res.ok) return
  recoveryCodes.value = res.data.recoveryCodes
  mfaSetup.value = null
  await auth.fetchMe()
}

async function disableMfa() {
  const res = await mfaRequest('/me/mfa/disable', {
    method: 'POST', body: { code: mfaCode.value }, success: 'Two-factor authentication turned off',
  })
  if (!res.ok) return
  disableDialog.value = false
  recoveryCodes.value = null
  await auth.fetchMe()
}

// ---------------------------------------------------------------- passkeys
const passkeyApi = usePasskeys()
const { data: passkeyList, error: passkeysError, refresh: reloadPasskeys } = useApi<PasskeyDto[]>('/me/passkeys', { server: false })
const passkeyName = ref('')
const addingPasskey = ref(false)

async function addPasskey() {
  addingPasskey.value = true
  try {
    await passkeyApi.register(passkeyName.value.trim() || device(navigator.userAgent))
    passkeyName.value = ''
    toast.add({ severity: 'success', summary: 'Passkey added', life: 3000 })
    await reloadPasskeys()
  } catch (e) {
    toast.add({ severity: 'error', summary: passkeyApi.messageOf(e), life: 6000 })
  } finally {
    addingPasskey.value = false
  }
}

async function removePasskey(p: PasskeyDto) {
  const res = await request(`/me/passkeys/${p.id}`, { method: 'DELETE', success: `Passkey "${p.name}" removed` })
  if (res.ok) await reloadPasskeys()
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
            <Password
id="p-cur" v-model="password.current" :feedback="false" toggle-mask
                      autocomplete="current-password" required fluid />
          </div>
          <div class="field">
            <label for="p-new">New password</label>
            <Password id="p-new" v-model="password.next" toggle-mask autocomplete="new-password" required fluid />
            <small class="muted">At least 10 characters with letters and digits.</small>
          </div>
          <div class="field">
            <label for="p-conf">Confirm new password</label>
            <Password
id="p-conf" v-model="password.confirm" :feedback="false" toggle-mask
                      autocomplete="new-password" required fluid />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" label="Update password" icon="pi pi-lock" :loading="savingPassword" />
          </div>
        </form>
      </template>
    </Card>

    <Card>
      <template #title>Passkeys</template>
      <template #subtitle>Sign in with your fingerprint, face or device PIN instead of a password. Works on this workspace only.</template>
      <template #content>
        <div class="stack">
          <LoadError :error="passkeysError" what="passkeys" @retry="reloadPasskeys()" />
          <DataTable v-if="!passkeysError" :value="passkeyList ?? []" data-key="id" size="small">
            <template #empty>No passkeys yet.</template>
            <Column field="name" header="Name" />
            <Column header="Added"><template #body="{ data: p }">{{ new Date(p.createdAt).toLocaleDateString() }}</template></Column>
            <Column header="Last used"><template #body="{ data: p }">{{ p.lastUsedAt ? new Date(p.lastUsedAt).toLocaleString() : 'never' }}</template></Column>
            <Column header="">
              <template #body="{ data: p }"><Tag v-if="p.synced" value="synced" severity="secondary" /></template>
            </Column>
            <Column style="width: 4rem; text-align: right">
              <template #body="{ data: p }">
                <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Remove passkey" @click="removePasskey(p)" />
              </template>
            </Column>
          </DataTable>
          <form v-if="passkeyApi.supported.value" class="row" @submit.prevent="addPasskey">
            <InputText v-model="passkeyName" placeholder="Name, e.g. MacBook" style="flex: 1" />
            <Button type="submit" label="Add a passkey" icon="pi pi-key" :loading="addingPasskey" />
          </form>
          <Message v-else severity="secondary" :closable="false">This browser does not support passkeys.</Message>
        </div>
      </template>
    </Card>

    <Card>
      <template #title>
        <span class="row">Two-factor authentication
          <Tag :value="auth.user?.mfaEnabled ? 'On' : 'Off'" :severity="auth.user?.mfaEnabled ? 'success' : 'secondary'" />
        </span>
      </template>
      <template #subtitle>Require a code from an authenticator app (Google Authenticator, 1Password, Authy…) at sign-in.</template>
      <template #content>
        <div v-if="recoveryCodes" class="stack">
          <Message severity="warn" :closable="false">
            Save these recovery codes now — they are shown only once. Each one works a single time if you lose your device.
          </Message>
          <div class="grid-2"><code v-for="c in recoveryCodes" :key="c">{{ c }}</code></div>
          <div><Button label="I saved them" icon="pi pi-check" @click="recoveryCodes = null" /></div>
        </div>

        <div v-else-if="auth.user?.mfaEnabled">
          <Button label="Turn off" icon="pi pi-lock-open" severity="danger" outlined @click="mfaCode = ''; disableDialog = true" />
        </div>

        <form v-else-if="mfaSetup" class="stack" @submit.prevent="enableMfa">
          <p>Scan the QR code with your authenticator app, then enter the 6-digit code it shows.</p>
          <div class="row" style="flex-wrap: wrap; gap: 1.5rem">
            <img :src="mfaSetup.qr" alt="Authenticator QR code" width="200" height="200" style="border-radius: 8px">
            <div class="stack">
              <span class="muted">Can't scan? Enter this key manually:</span>
              <code style="word-break: break-all">{{ mfaSetup.secret }}</code>
              <InputOtp v-model="mfaCode" :length="6" integer-only />
            </div>
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="button" label="Cancel" severity="secondary" text @click="mfaSetup = null" />
            <Button type="submit" label="Turn on" icon="pi pi-shield" :loading="mfaBusy" :disabled="mfaCode.length !== 6" />
          </div>
        </form>

        <div v-else>
          <Button label="Set up" icon="pi pi-shield" :loading="mfaBusy" @click="beginMfaSetup" />
        </div>
      </template>
    </Card>

    <Dialog v-model:visible="disableDialog" header="Turn off two-factor authentication" modal :style="{ width: '26rem' }">
      <form class="stack" @submit.prevent="disableMfa">
        <div class="field">
          <label for="mfa-off">Authenticator or recovery code</label>
          <InputText id="mfa-off" v-model="mfaCode" autocomplete="one-time-code" required fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" label="Cancel" severity="secondary" text @click="disableDialog = false" />
          <Button type="submit" label="Turn off" severity="danger" :loading="mfaBusy" />
        </div>
      </form>
    </Dialog>

    <Card>
      <template #title>Active sessions</template>
      <template #subtitle>Devices that can still refresh their sign-in. A revoked device is signed out within 15 minutes.</template>
      <template #content>
        <LoadError :error="sessionsError" what="sessions" @retry="reloadSessions()" />
        <DataTable v-if="!sessionsError" :value="sessions ?? []" data-key="id" size="small">
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
