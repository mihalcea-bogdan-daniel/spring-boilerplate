<script setup lang="ts">
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
  </div>
</template>
