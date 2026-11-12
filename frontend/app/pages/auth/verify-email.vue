<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const auth = useAuthStore()
const toast = useToast()
const route = useRoute()
const email = ref(typeof route.query.email === 'string' ? route.query.email : '')
const code = ref('')
const loading = ref(false)
const error = ref<string | null>(null)

async function submit() {
  loading.value = true
  error.value = null
  try {
    await auth.verifyEmail(email.value, code.value)
    await navigateTo('/')
  } catch (e) {
    error.value = problemOf(e).detail
  } finally {
    loading.value = false
  }
}

async function resend() {
  try {
    const res = await auth.resendVerification(email.value)
    toast.add({ severity: 'info', summary: res.message, life: 4000 })
  } catch (e) {
    toast.add({ severity: 'warn', summary: problemOf(e).detail, life: 4000 })
  }
}
</script>

<template>
  <Card>
    <template #title>Verify your e-mail</template>
    <template #subtitle>We sent a 6-digit code to {{ email || 'your inbox' }}.</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <div v-if="!route.query.email" class="field">
          <label for="email">E-mail</label>
          <InputText id="email" v-model="email" type="email" required fluid />
        </div>
        <div class="row" style="justify-content: center">
          <InputOtp v-model="code" :length="6" integer-only />
        </div>
        <Button type="submit" label="Verify" :loading="loading" :disabled="code.length !== 6" fluid />
        <Button type="button" label="Resend code" link @click="resend" />
      </form>
    </template>
  </Card>
</template>
