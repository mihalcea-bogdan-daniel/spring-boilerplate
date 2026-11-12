<script setup lang="ts">
import QRCode from 'qrcode'
import type { PasskeyDto, SessionDto } from '~/types/api'

const auth = useAuthStore()
const { t, d } = useI18n()
const toast = useToast()
const { request, notifyError } = useRequest()
const { request: mfaRequest, pending: mfaBusy } = useRequest()

const profile = reactive({ firstName: auth.user?.firstName ?? '', lastName: auth.user?.lastName ?? '' })
const savingProfile = ref(false)
async function saveProfile() {
  savingProfile.value = true
  try {
    await auth.updateProfile(profile.firstName, profile.lastName)
    toast.add({ severity: 'success', summary: t('profile.updated'), life: 3000 })
  } catch (e) {
    notifyError(e, t('profile.updateFailed'))
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
    passwordError.value = t('profile.passwordMismatch')
    return
  }
  savingPassword.value = true
  try {
    await auth.changePassword(auth.user?.hasPassword ? password.current : null, password.next)
    toast.add({ severity: 'success', summary: t('profile.passwordUpdated'), detail: t('profile.signInAgain'), life: 4000 })
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
  const res = await request(`/me/sessions/${s.id}`, { method: 'DELETE', success: t('profile.sessionRevoked') })
  if (res.ok) await reloadSessions()
}

async function signOutEverywhere() {
  const res = await request('/me/sessions', { method: 'DELETE', success: false, error: t('profile.signOutFailed') })
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
    method: 'POST', body: { code: mfaCode.value }, success: t('profile.mfaOn'),
  })
  if (!res.ok) return
  recoveryCodes.value = res.data.recoveryCodes
  mfaSetup.value = null
  await auth.fetchMe()
}

async function disableMfa() {
  const res = await mfaRequest('/me/mfa/disable', {
    method: 'POST', body: { code: mfaCode.value }, success: t('profile.mfaOff'),
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
    toast.add({ severity: 'success', summary: t('profile.passkeyAdded'), life: 3000 })
    await reloadPasskeys()
  } catch (e) {
    toast.add({ severity: 'error', summary: passkeyApi.messageOf(e), life: 6000 })
  } finally {
    addingPasskey.value = false
  }
}

async function removePasskey(p: PasskeyDto) {
  const res = await request(`/me/passkeys/${p.id}`, { method: 'DELETE', success: t('profile.passkeyRemoved', { name: p.name }) })
  if (res.ok) await reloadPasskeys()
}

/** Short human label for a User-Agent string. */
function device(ua: string | null): string {
  if (!ua) return t('profile.unknownDevice')
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Mac OS X/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : ''
  return os ? t('profile.browserOn', { browser, os }) : browser
}
</script>

<template>
  <div class="stack" style="max-width: 40rem">
    <h1 class="page-title">{{ t('profile.title') }}</h1>

    <Card>
      <template #title>{{ t('profile.personal') }}</template>
      <template #subtitle>{{ auth.user?.email }}</template>
      <template #content>
        <form class="stack" @submit.prevent="saveProfile">
          <div class="grid-2">
            <div class="field">
              <label for="p-fn">{{ t('common.firstName') }}</label>
              <InputText id="p-fn" v-model="profile.firstName" autocomplete="given-name" fluid />
            </div>
            <div class="field">
              <label for="p-ln">{{ t('common.lastName') }}</label>
              <InputText id="p-ln" v-model="profile.lastName" autocomplete="family-name" fluid />
            </div>
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="savingProfile" />
          </div>
        </form>
      </template>
    </Card>

    <Card>
      <template #title>{{ auth.user?.hasPassword ? t('profile.changePassword') : t('profile.setPassword') }}</template>
      <template #subtitle>
        {{ auth.user?.hasPassword ? t('profile.changePasswordHint') : t('profile.setPasswordHint') }}
      </template>
      <template #content>
        <form class="stack" @submit.prevent="savePassword">
          <Message v-if="passwordError" severity="error" :closable="false">{{ passwordError }}</Message>
          <div v-if="auth.user?.hasPassword" class="field">
            <label for="p-cur">{{ t('profile.currentPassword') }}</label>
            <Password
id="p-cur" v-model="password.current" :feedback="false" toggle-mask
                      autocomplete="current-password" required fluid />
          </div>
          <div class="field">
            <label for="p-new">{{ t('auth.newPassword') }}</label>
            <Password id="p-new" v-model="password.next" toggle-mask autocomplete="new-password" required fluid />
            <small class="muted">{{ t('common.passwordHint') }}</small>
          </div>
          <div class="field">
            <label for="p-conf">{{ t('profile.confirmPassword') }}</label>
            <Password
id="p-conf" v-model="password.confirm" :feedback="false" toggle-mask
                      autocomplete="new-password" required fluid />
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="submit" :label="t('auth.updatePassword')" icon="pi pi-lock" :loading="savingPassword" />
          </div>
        </form>
      </template>
    </Card>

    <Card>
      <template #title>{{ t('profile.passkeys') }}</template>
      <template #subtitle>{{ t('profile.passkeysHint') }}</template>
      <template #content>
        <div class="stack">
          <LoadError :error="passkeysError" :what="t('profile.passkeysWhat')" @retry="reloadPasskeys()" />
          <DataTable v-if="!passkeysError" :value="passkeyList ?? []" data-key="id" size="small">
            <template #empty>{{ t('profile.noPasskeys') }}</template>
            <Column field="name" :header="t('common.name')" />
            <Column :header="t('profile.added')"><template #body="{ data: p }">{{ d(p.createdAt, 'date') }}</template></Column>
            <Column :header="t('common.lastUsed')"><template #body="{ data: p }">{{ p.lastUsedAt ? d(p.lastUsedAt, 'datetime') : t('common.never') }}</template></Column>
            <Column header="">
              <template #body="{ data: p }"><Tag v-if="p.synced" :value="t('profile.synced')" severity="secondary" /></template>
            </Column>
            <Column style="width: 4rem; text-align: right">
              <template #body="{ data: p }">
                <Button icon="pi pi-trash" text rounded severity="danger" :aria-label="t('profile.removePasskey')" @click="removePasskey(p)" />
              </template>
            </Column>
          </DataTable>
          <form v-if="passkeyApi.supported.value" class="row" @submit.prevent="addPasskey">
            <InputText v-model="passkeyName" :placeholder="t('profile.passkeyPlaceholder')" style="flex: 1" />
            <Button type="submit" :label="t('profile.addPasskey')" icon="pi pi-key" :loading="addingPasskey" />
          </form>
          <Message v-else severity="secondary" :closable="false">{{ t('profile.passkeysUnsupported') }}</Message>
        </div>
      </template>
    </Card>

    <Card>
      <template #title>
        <span class="row">{{ t('profile.mfa') }}
          <Tag :value="auth.user?.mfaEnabled ? t('profile.on') : t('profile.off')" :severity="auth.user?.mfaEnabled ? 'success' : 'secondary'" />
        </span>
      </template>
      <template #subtitle>{{ t('profile.mfaHint') }}</template>
      <template #content>
        <div v-if="recoveryCodes" class="stack">
          <Message severity="warn" :closable="false">
            {{ t('profile.recoveryWarning') }}
          </Message>
          <div class="grid-2"><code v-for="c in recoveryCodes" :key="c">{{ c }}</code></div>
          <div><Button :label="t('profile.savedThem')" icon="pi pi-check" @click="recoveryCodes = null" /></div>
        </div>

        <div v-else-if="auth.user?.mfaEnabled">
          <Button :label="t('profile.turnOff')" icon="pi pi-lock-open" severity="danger" outlined @click="mfaCode = ''; disableDialog = true" />
        </div>

        <form v-else-if="mfaSetup" class="stack" @submit.prevent="enableMfa">
          <p>{{ t('profile.scanQr') }}</p>
          <div class="row" style="flex-wrap: wrap; gap: 1.5rem">
            <img :src="mfaSetup.qr" :alt="t('profile.qrAlt')" width="200" height="200" style="border-radius: 8px">
            <div class="stack">
              <span class="muted">{{ t('profile.cantScan') }}</span>
              <code style="word-break: break-all">{{ mfaSetup.secret }}</code>
              <InputOtp v-model="mfaCode" :length="6" integer-only />
            </div>
          </div>
          <div class="row" style="justify-content: flex-end">
            <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="mfaSetup = null" />
            <Button type="submit" :label="t('profile.turnOn')" icon="pi pi-shield" :loading="mfaBusy" :disabled="mfaCode.length !== 6" />
          </div>
        </form>

        <div v-else>
          <Button :label="t('profile.setUp')" icon="pi pi-shield" :loading="mfaBusy" @click="beginMfaSetup" />
        </div>
      </template>
    </Card>

    <Dialog v-model:visible="disableDialog" :header="t('profile.turnOffMfa')" modal :style="{ width: '26rem' }">
      <form class="stack" @submit.prevent="disableMfa">
        <div class="field">
          <label for="mfa-off">{{ t('profile.mfaOffCode') }}</label>
          <InputText id="mfa-off" v-model="mfaCode" autocomplete="one-time-code" required fluid />
        </div>
        <div class="row" style="justify-content: flex-end">
          <Button type="button" :label="t('common.cancel')" severity="secondary" text @click="disableDialog = false" />
          <Button type="submit" :label="t('profile.turnOff')" severity="danger" :loading="mfaBusy" />
        </div>
      </form>
    </Dialog>

    <Card>
      <template #title>{{ t('profile.sessions') }}</template>
      <template #subtitle>{{ t('profile.sessionsHint') }}</template>
      <template #content>
        <LoadError :error="sessionsError" :what="t('profile.sessionsWhat')" @retry="reloadSessions()" />
        <DataTable v-if="!sessionsError" :value="sessions ?? []" data-key="id" size="small">
          <template #empty>{{ t('profile.noSessions') }}</template>
          <Column :header="t('profile.device')">
            <template #body="{ data: s }">{{ device(s.userAgent) }}</template>
          </Column>
          <Column field="ipAddress" :header="t('audit.ip')" />
          <Column :header="t('profile.signedIn')">
            <template #body="{ data: s }">{{ d(s.createdAt, 'datetime') }}</template>
          </Column>
          <Column style="width: 4rem; text-align: right">
            <template #body="{ data: s }">
              <Button icon="pi pi-times" text rounded severity="danger" :aria-label="t('common.revoke')" @click="revokeSession(s)" />
            </template>
          </Column>
        </DataTable>
        <div class="row" style="justify-content: flex-end; margin-top: 1rem">
          <Button :label="t('profile.signOutEverywhere')" icon="pi pi-sign-out" severity="danger" outlined @click="signOutEverywhere" />
        </div>
      </template>
    </Card>
  </div>
</template>
