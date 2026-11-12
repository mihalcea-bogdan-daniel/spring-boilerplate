<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const { t } = useI18n()

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
    <template #title>{{ t('auth.verifyTitle') }}</template>
    <template #subtitle>{{ t('auth.verifySent', { email: email || t('auth.yourInbox') }) }}</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <div v-if="!route.query.email" class="field">
          <label for="email">{{ t('common.email') }}</label>
          <InputText id="email" v-model="email" type="email" required fluid />
        </div>
        <div class="row" style="justify-content: center">
          <InputOtp v-model="code" :length="6" integer-only />
        </div>
        <Button type="submit" :label="t('common.verify')" :loading="loading" :disabled="code.length !== 6" fluid />
        <Button type="button" :label="t('auth.resend')" link @click="resend" />
      </form>
    </template>
  </Card>
</template>
