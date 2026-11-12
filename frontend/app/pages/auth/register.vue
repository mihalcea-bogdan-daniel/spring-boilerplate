<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

const { t } = useI18n()

const auth = useAuthStore()
const form = reactive({ firstName: '', lastName: '', email: '', password: '' })
const loading = ref(false)
const error = ref<string | null>(null)
const fieldErrors = ref<Record<string, string>>({})

async function submit() {
  loading.value = true
  error.value = null
  fieldErrors.value = {}
  try {
    await auth.register(form)
    await navigateTo({ path: '/auth/verify-email', query: { email: form.email } })
  } catch (e) {
    const p = problemOf(e)
    error.value = p.detail
    fieldErrors.value = p.errors ?? {}
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Card>
    <template #title>{{ t('auth.registerTitle') }}</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <div class="grid-2">
          <div class="field">
            <label for="fn">{{ t('common.firstName') }}</label>
            <InputText id="fn" v-model="form.firstName" autocomplete="given-name" fluid />
          </div>
          <div class="field">
            <label for="ln">{{ t('common.lastName') }}</label>
            <InputText id="ln" v-model="form.lastName" autocomplete="family-name" fluid />
          </div>
        </div>
        <div class="field">
          <label for="email">{{ t('common.email') }}</label>
          <InputText
id="email" v-model="form.email" type="email" autocomplete="email" required
                     :invalid="!!fieldErrors.email" fluid />
          <small v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</small>
        </div>
        <div class="field">
          <label for="pw">{{ t('common.password') }}</label>
          <Password
id="pw" v-model="form.password" toggle-mask autocomplete="new-password" required
                    :invalid="!!fieldErrors.password" fluid />
          <small v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</small>
          <small class="muted">{{ t('common.passwordHint') }}</small>
        </div>
        <Button type="submit" :label="t('auth.createAccount')" :loading="loading" fluid />
      </form>
    </template>
    <template #footer>
      <p class="muted">{{ t('auth.alreadyRegistered') }} <NuxtLink to="/auth/login">{{ t('auth.signIn') }}</NuxtLink></p>
    </template>
  </Card>
</template>
