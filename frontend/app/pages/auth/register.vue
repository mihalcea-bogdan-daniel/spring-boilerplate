<script setup lang="ts">
definePageMeta({ layout: 'auth', public: true })

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
    <template #title>Create your account</template>
    <template #content>
      <form class="stack" @submit.prevent="submit">
        <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
        <div class="grid-2">
          <div class="field">
            <label for="fn">First name</label>
            <InputText id="fn" v-model="form.firstName" autocomplete="given-name" fluid />
          </div>
          <div class="field">
            <label for="ln">Last name</label>
            <InputText id="ln" v-model="form.lastName" autocomplete="family-name" fluid />
          </div>
        </div>
        <div class="field">
          <label for="email">E-mail</label>
          <InputText id="email" v-model="form.email" type="email" autocomplete="email" required
                     :invalid="!!fieldErrors.email" fluid />
          <small v-if="fieldErrors.email" class="error">{{ fieldErrors.email }}</small>
        </div>
        <div class="field">
          <label for="pw">Password</label>
          <Password id="pw" v-model="form.password" toggle-mask autocomplete="new-password" required
                    :invalid="!!fieldErrors.password" fluid />
          <small v-if="fieldErrors.password" class="error">{{ fieldErrors.password }}</small>
          <small class="muted">At least 10 characters with letters and digits.</small>
        </div>
        <Button type="submit" label="Create account" :loading="loading" fluid />
      </form>
    </template>
    <template #footer>
      <p class="muted">Already registered? <NuxtLink to="/auth/login">Sign in</NuxtLink></p>
    </template>
  </Card>
</template>
