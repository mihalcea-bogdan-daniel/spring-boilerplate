<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const auth = useAuthStore()
const step = ref<'email' | 'code'>('email')
const route = useRoute()
// invitation e-mails link to /auth/otp?email=...
const email = ref(typeof route.query.email === 'string' ? route.query.email : '')
const code = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

async function requestCode() {
  loading.value = true
  error.value = null
  try {
    await auth.requestOtp(email.value)
    step.value = 'code'
  } catch (e) {
    error.value = problemOf(e).detail
  } finally {
    loading.value = false
  }
}

async function verify() {
  loading.value = true
  error.value = null
  try {
    await auth.verifyOtp(email.value, code.value)
    await navigateTo('/')
  } catch (e) {
    error.value = problemOf(e).detail
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Card>
    <template #title>Sign in with a code</template>
    <template #content>
      <Message v-if="error" severity="error" :closable="false" class="mb-3">{{ error }}</Message>
      <form v-if="step === 'email'" class="stack" @submit.prevent="requestCode">
        <div class="field">
          <label for="email">E-mail</label>
          <InputText id="email" v-model="email" type="email" autocomplete="email" required fluid />
        </div>
        <Button type="submit" label="Send code" icon="pi pi-envelope" :loading="loading" fluid />
      </form>
      <form v-else class="stack" @submit.prevent="verify">
        <p class="muted">If {{ email }} has an account, a code is on its way.</p>
        <div class="row" style="justify-content: center">
          <InputOtp v-model="code" :length="6" integer-only />
        </div>
        <Button type="submit" label="Sign in" :loading="loading" :disabled="code.length !== 6" fluid />
        <Button type="button" label="Use another e-mail" link @click="step = 'email'; code = ''" />
      </form>
    </template>
    <template #footer>
      <NuxtLink to="/auth/login" class="muted">Back to password sign-in</NuxtLink>
    </template>
  </Card>
</template>
