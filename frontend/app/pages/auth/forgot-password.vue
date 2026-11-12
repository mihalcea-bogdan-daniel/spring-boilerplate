<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const { t } = useI18n()

const auth = useAuthStore()
const toast = useToast()
const step = ref<'request' | 'reset'>('request')
const form = reactive({ email: '', code: '', password: '' })
const loading = ref(false)
const error = ref<string | null>(null)

async function request() {
  loading.value = true
  error.value = null
  try {
    await auth.forgotPassword(form.email)
    step.value = 'reset'
  } catch (e) {
    error.value = problemOf(e).detail
  } finally {
    loading.value = false
  }
}

async function reset() {
  loading.value = true
  error.value = null
  try {
    const res = await auth.resetPassword(form.email, form.code, form.password)
    toast.add({ severity: 'success', summary: res.message, life: 4000 })
    await navigateTo('/auth/login')
  } catch (e) {
    const p = problemOf(e)
    error.value = p.errors?.newPassword ?? p.detail
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Card>
    <template #title>{{ t('auth.resetTitle') }}</template>
    <template #content>
      <Message v-if="error" severity="error" :closable="false" class="mb-3">{{ error }}</Message>
      <form v-if="step === 'request'" class="stack" @submit.prevent="request">
        <div class="field">
          <label for="email">{{ t('common.email') }}</label>
          <InputText id="email" v-model="form.email" type="email" required fluid />
        </div>
        <Button type="submit" :label="t('auth.sendResetCode')" :loading="loading" fluid />
      </form>
      <form v-else class="stack" @submit.prevent="reset">
        <div class="row" style="justify-content: center">
          <InputOtp v-model="form.code" :length="6" integer-only />
        </div>
        <div class="field">
          <label for="pw">{{ t('auth.newPassword') }}</label>
          <Password id="pw" v-model="form.password" toggle-mask autocomplete="new-password" required fluid />
        </div>
        <Button type="submit" :label="t('auth.updatePassword')" :loading="loading" fluid />
      </form>
    </template>
  </Card>
</template>
