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

export interface PasskeyDto {
  id: string
  name: string
  createdAt: string
  lastUsedAt: string | null
  /** Synced through a password manager / platform account (iCloud Keychain, Google Password Manager...). */
  synced: boolean
}

export interface ApiKeyDto {
  id: string
  name: string
  keyPrefix: string
  permissions: string[]
  createdBy: string | null
  createdAt: string
  lastUsedAt: string | null
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
  /** Same value as the X-Request-Id header and the server logs. */
  requestId?: string
}

// ---------------------------------------------------------------- organisation (com.polygis.org)

/** A numbered function (Director 900, Chief 100…). `labels` holds the name per locale code. */
export interface RankDto {
  id: string
  key: string
  level: number
  labels: Record<string, string>
  aliases: string[]
  supervises: boolean
}

export interface OrgMemberDto {
  userId: string
  email: string
  name: string
  rankId: string
  level: number
}

/** One unit of the organigram. `leadLevel` is the caller's level over it, null when they don't lead it. */
export interface ChartNode {
  id: string
  parentId: string | null
  name: string
  sortOrder: number
  members: OrgMemberDto[]
  leadLevel: number | null
  children: ChartNode[]
}

export interface PersonDto {
  id: string
  email: string
  name: string
}

export interface SubordinateDto {
  id: string
  email: string
  name: string
  status: UserStatus
  lastLoginAt: string | null
  placements: { unitId: string, unitName: string, rankId: string, level: number }[]
}

// ---------------------------------------------------------------- access model (com.polygis.access)

export type SubjectKind = 'USER' | 'UNIT' | 'GROUP'

export interface SubjectDto {
  kind: SubjectKind
  id: string
  name: string
  /** e-mail for people, group kind for groups */
  detail: string | null
}

export interface AccessDto {
  type: string
  id: string
  /** action -> allowed, e.g. { read: true, write: false, delete: false, share: false } */
  can: Record<string, boolean>
  relationNames: string[]
  ownerRelation: string
  relations: { relation: string, subject: SubjectDto }[]
}

export type AccessScope = 'VISIBLE' | 'MINE' | 'TEAM' | 'ALL'

export interface ProjectDto {
  id: string
  name: string
  description: string | null
  owners: SubjectDto[]
  createdAt: string
  updatedAt: string
}

export interface GroupMemberDto {
  userId: string
  email: string
  name: string
  role: 'admin' | 'member'
}

export interface GroupDto {
  id: string
  kind: string
  name: string
  members: GroupMemberDto[]
  canManage: boolean
}
