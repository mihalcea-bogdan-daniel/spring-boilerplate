import { defineStore } from 'pinia'
import type { Me, MessageResponse, MfaChallengeResponse, TokenResponse } from '~/types/api'

/**
 * Session state.
 * - access token: short-lived JWT kept in a (non-HttpOnly) SameSite=Strict cookie so SSR requests can use it;
 * - refresh token: HttpOnly cookie owned by the backend (path /api/auth) - JavaScript never sees it.
 * - impersonated session ("sign in as"): access token only, kept in this tab's sessionStorage. Never in the shared
 *   cookie, so the person who started it stays signed in as themself in every other tab.
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

  const IMPERSONATION_KEY = 'impersonation_token'
  const impersonationToken = ref<string | null>(import.meta.client ? readTab(IMPERSONATION_KEY) : null)
  /** The bearer token for API calls: this tab's impersonated session if any, else the user's own. */
  const token = computed(() => impersonationToken.value ?? accessToken.value)
  const isImpersonating = computed(() => !!impersonationToken.value)

  const isAuthenticated = computed(() => !!token.value && !!user.value)
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
  /** @returns false when the account requires its second factor (continue at /auth/mfa). */
  async function exchangeOAuth2Code(code: string): Promise<boolean> {
    return completeFirstFactor(await nuxtApp.$api<TokenResponse | MfaChallengeResponse>(
      '/auth/oauth2/exchange', { method: 'POST', body: { code } }))
  }

  /** "Sign in as" landing: /auth/callback?impersonate=... The session lives in this tab only (see top). */
  async function exchangeImpersonationCode(code: string) {
    const res = await nuxtApp.$api<TokenResponse>('/auth/impersonation/exchange', { method: 'POST', body: { code } })
    impersonationToken.value = res.accessToken
    writeTab(IMPERSONATION_KEY, res.accessToken)
    user.value = res.user
  }

  /** Leaves the impersonated session in this tab; the user's own session (cookie) is untouched. */
  function endImpersonation() {
    impersonationToken.value = null
    writeTab(IMPERSONATION_KEY, null)
    user.value = null
  }

  /** Full-page redirect into Spring's OAuth2 flow on the current (tenant) host. */
  function startSocialLogin(provider: string, redirect = '/') {
    const base = useRuntimeConfig().public.apiBase
    window.location.href = `${base}/oauth2/authorization/${provider}?redirect=${encodeURIComponent(redirect)}`
  }

  // Concurrent 401s share one refresh round-trip.
  let refreshing: Promise<boolean> | null = null

  function refresh(): Promise<boolean> {
    // an impersonated session can't be refreshed: when it expires this tab returns to the user's own session
    if (impersonationToken.value) endImpersonation()
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
    // the server rendered with the shared cookie; this tab may be impersonating someone else
    if (impersonationToken.value && user.value?.impersonatedBy == null) {
      try {
        await fetchMe()
        return true
      } catch {
        endImpersonation()
      }
    }
    if (user.value && token.value) return true
    if (token.value) {
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
    if (impersonationToken.value) {
      endImpersonation()
      await navigateTo('/', { external: true }) // reload as yourself
      return
    }
    try {
      await nuxtApp.$api('/auth/logout', { method: 'POST' })
    } finally {
      clearSession()
      await navigateTo('/auth/login')
    }
  }

  return {
    accessToken, token, isImpersonating, endImpersonation, user, mfaToken, verifyMfa, isAuthenticated, isSuperAdmin, displayName, can,
    setSession, clearSession, login, register, verifyEmail, resendVerification, requestOtp, verifyOtp,
    forgotPassword, resetPassword, exchangeOAuth2Code, exchangeImpersonationCode, startSocialLogin, refresh, fetchMe, ensureSession, updateProfile, changePassword, logout,
  }
})

/** Per-tab storage; unavailable storage (private mode, blocked) simply means no persistence across reloads. */
function readTab(key: string): string | null {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function writeTab(key: string, value: string | null) {
  try {
    if (value) sessionStorage.setItem(key, value)
    else sessionStorage.removeItem(key)
  } catch { /* ignore */ }
}
