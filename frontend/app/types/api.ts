export interface Me {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  tenant: string
  roles: string[]
  permissions: string[]
  superAdmin: boolean
  hasPassword: boolean
  mfaEnabled: boolean
  /** E-mail of the super-admin when this is an impersonated session, otherwise null. */
  impersonatedBy: string | null
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: Me
}

/** Returned by login endpoints instead of tokens when the account has two-factor authentication. */
export interface MfaChallengeResponse {
  mfaRequired: true
  mfaToken: string
}

export interface MessageResponse {
  message: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  page: number
  size: number
}

export type UserStatus = 'PENDING' | 'ACTIVE' | 'DISABLED'

export interface UserDto {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  status: UserStatus
  emailVerified: boolean
  roles: string[]
  lastLoginAt: string | null
  createdAt: string
}

export interface RoleDto {
  id: string
  name: string
  description: string | null
  systemRole: boolean
  permissions: string[]
}

export interface PermissionDto {
  code: string
  description: string | null
}

export type TenantStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED'

export interface TenantDto {
  id: string
  slug: string
  name: string
  schemaName: string
  domain: string | null
  status: TenantStatus
  plan: string
  createdAt: string
}

export interface TenantStatsDto {
  slug: string
  schemaVersion: string | null
  latestVersion: string | null
  upToDate: boolean
  lastMigratedAt: string | null
  sizeBytes: number
  users: number
  activeSessions: number
}

export interface SessionDto {
  id: string
  userAgent: string | null
  ipAddress: string | null
  createdAt: string
  expiresAt: string
}

export interface AuditEventDto {
  id: string
  occurredAt: string
  actorEmail: string | null
  actorTenant: string | null
  action: string
  targetType: string | null
  targetId: string | null
  details: string | null
  ipAddress: string | null
}

/** RFC 7807 body produced by GlobalExceptionHandler. */
export interface Problem {
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: Record<string, string>
}
