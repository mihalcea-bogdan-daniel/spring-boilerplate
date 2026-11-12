import { defineStore } from 'pinia'
import type { Me, MessageResponse, MfaChallengeResponse, TokenResponse } from '~/types/api'

/**
 * Session state.
 * - access token: short-lived JWT kept in a (non-HttpOnly) SameSite=Strict cookie so SSR requests can use it;
 * - refresh token: HttpOnly cookie owned by the backend (path /api/auth) - JavaScript never sees it.
 */
export const useAuthStore = defineStore('auth', () => {
  const nuxtApp = useNuxtApp()

  const accessToken = useCookie<string | null>('access_token', {
    sameSite: 'strict',
    secure: !import.meta.dev,
    maxAge: 15 * 60,
    default: () => null,
  })
  const user = ref<Me | null>(null)

  const isAuthenticated = computed(() => !!accessToken.value && !!user.value)
  const isSuperAdmin = computed(() => user.value?.superAdmin === true)
  const displayName = computed(() => {
    const u = user.value
    if (!u) return ''
    return [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email
  })

  function can(permission: string): boolean {
    return !!user.value?.permissions.includes(permission)
  }

  function setSession(res: TokenResponse) {
    accessToken.value = res.accessToken
    user.value = res.user
  }

  function clearSession() {
    accessToken.value = null
    user.value = null
    useTenantStore().reset()
  }

  // ------------------------------------------------------------------ flows

  /** Pending second-factor challenge (kept in memory only; it expires after 5 minutes). */
  const mfaToken = ref<string | null>(null)

  /** @returns true when signed in, false when a second factor is still required (go to /auth/mfa). */
  function completeFirstFactor(res: TokenResponse | MfaChallengeResponse): boolean {
    if ('mfaRequired' in res) {
      mfaToken.value = res.mfaToken
      return false
    }
    setSession(res)
    return true
  }

  async function login(email: string, password: string): Promise<boolean> {
    return completeFirstFactor(await nuxtApp.$api<TokenResponse | MfaChallengeResponse>(
      '/auth/login', { method: 'POST', body: { email, password } }))
  }

  async function verifyMfa(code: string) {
    setSession(await nuxtApp.$api<TokenResponse>('/auth/mfa/verify', {
      method: 'POST', body: { mfaToken: mfaToken.value, code },
    }))
    mfaToken.value = null
  }

  async function register(payload: { email: string, password: string, firstName?: string, lastName?: string }) {
    return nuxtApp.$api<MessageResponse>('/auth/register', { method: 'POST', body: payload })
  }

  async function verifyEmail(email: string, code: string) {
    setSession(await nuxtApp.$api<TokenResponse>('/auth/verify-email', { method: 'POST', body: { email, code } }))
  }

  async function resendVerification(email: string) {
    return nuxtApp.$api<MessageResponse>('/auth/verify-email/resend', { method: 'POST', body: { email } })
  }

  async function requestOtp(email: string) {
    return nuxtApp.$api<MessageResponse>('/auth/otp/request', { method: 'POST', body: { email } })
  }

  async function verifyOtp(email: string, code: string): Promise<boolean> {
    return completeFirstFactor(await nuxtApp.$api<TokenResponse | MfaChallengeResponse>(
      '/auth/otp/verify', { method: 'POST', body: { email, code } }))
  }

  async function forgotPassword(email: string) {
    return nuxtApp.$api<MessageResponse>('/auth/password/forgot', { method: 'POST', body: { email } })
  }

  async function resetPassword(email: string, code: string, newPassword: string) {
    return nuxtApp.$api<MessageResponse>('/auth/password/reset', {
      method: 'POST', body: { email, code, newPassword },
    })
  }

  /** Second leg of social login: /auth/callback?code=... */
  async function exchangeOAuth2Code(code: string) {
    setSession(await nuxtApp.$api<TokenResponse>('/auth/oauth2/exchange', { method: 'POST', body: { code } }))
  }

  /** "Sign in as user" landing: /auth/callback?impersonate=... (access token only, no refresh cookie). */
  async function exchangeImpersonationCode(code: string) {
    setSession(await nuxtApp.$api<TokenResponse>('/auth/impersonation/exchange', { method: 'POST', body: { code } }))
  }

  /** Full-page redirect into Spring's OAuth2 flow on the current (tenant) host. */
  function startSocialLogin(provider: string, redirect = '/') {
    const base = useRuntimeConfig().public.apiBase
    window.location.href = `${base}/oauth2/authorization/${provider}?redirect=${encodeURIComponent(redirect)}`
  }

  // Concurrent 401s share one refresh round-trip.
  let refreshing: Promise<boolean> | null = null

  function refresh(): Promise<boolean> {
    refreshing ??= nuxtApp.$api<TokenResponse>('/auth/refresh', { method: 'POST' })
      .then((res) => { setSession(res); return true })
      .catch(() => { clearSession(); return false })
      .finally(() => { refreshing = null })
    return refreshing
  }

  async function fetchMe() {
    user.value = await nuxtApp.$api<Me>('/auth/me')
  }

  /** Called by the auth middleware: restore the session from cookies (access token, else refresh cookie). */
  async function ensureSession(): Promise<boolean> {
    if (user.value && accessToken.value) return true
    if (accessToken.value) {
      try {
        await fetchMe()
        return true
      } catch { /* expired -> try refresh below */ }
    }
    return import.meta.client ? refresh() : false
  }

  async function updateProfile(firstName: string, lastName: string) {
    user.value = await nuxtApp.$api<Me>('/me', { method: 'PUT', body: { firstName, lastName } })
  }

  /** The backend revokes every session on password change, so the caller should sign in again. */
  async function changePassword(currentPassword: string | null, newPassword: string) {
    await nuxtApp.$api('/me/password', { method: 'PUT', body: { currentPassword, newPassword } })
  }

  async function logout() {
    try {
      await nuxtApp.$api('/auth/logout', { method: 'POST' })
    } finally {
      clearSession()
      await navigateTo('/auth/login')
    }
  }

  return {
    accessToken, user, mfaToken, verifyMfa, isAuthenticated, isSuperAdmin, displayName, can,
    setSession, clearSession, login, register, verifyEmail, resendVerification, requestOtp, verifyOtp,
    forgotPassword, resetPassword, exchangeOAuth2Code, exchangeImpersonationCode, startSocialLogin, refresh, fetchMe, ensureSession, updateProfile, changePassword, logout,
  }
})
