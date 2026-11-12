import {
  browserSupportsWebAuthn,
  startAuthentication,
  startRegistration,
  type PublicKeyCredentialCreationOptionsJSON,
  type PublicKeyCredentialRequestOptionsJSON,
} from '@simplewebauthn/browser'
import type { PasskeyDto, TokenResponse } from '~/types/api'

/**
 * Passkey ceremonies. The server builds the options (rpId = this workspace's host) and verifies the result;
 * the browser talks to the authenticator (Touch ID, Windows Hello, a phone, a security key...).
 */
export function usePasskeys() {
  const nuxtApp = useNuxtApp()
  const auth = useAuthStore()
  const supported = ref(false)
  onMounted(() => { supported.value = browserSupportsWebAuthn() })

  async function register(name: string): Promise<PasskeyDto> {
    const optionsJSON = await nuxtApp.$api<PublicKeyCredentialCreationOptionsJSON>('/me/passkeys/options', { method: 'POST' })
    const credential = await startRegistration({ optionsJSON })
    return nuxtApp.$api<PasskeyDto>('/me/passkeys', { method: 'POST', body: { name, credential } })
  }

  /** Signs in without e-mail or password; a passkey with biometrics/PIN also satisfies two-factor. */
  async function signIn() {
    const optionsJSON = await nuxtApp.$api<PublicKeyCredentialRequestOptionsJSON>('/auth/passkey/options', { method: 'POST' })
    const credential = await startAuthentication({ optionsJSON })
    auth.setSession(await nuxtApp.$api<TokenResponse>('/auth/passkey/verify', { method: 'POST', body: credential }))
  }

  /** Readable message for both API problems and the browser's WebAuthn errors. */
  function messageOf(error: unknown): string {
    const name = (error as { name?: string } | null)?.name
    if (name === 'NotAllowedError' || name === 'AbortError') return 'The passkey prompt was cancelled or timed out.'
    if (name === 'InvalidStateError') return 'This device already has a passkey for your account.'
    return problemOf(error).detail
  }

  return { supported, register, signIn, messageOf }
}
