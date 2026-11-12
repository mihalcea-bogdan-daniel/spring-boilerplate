export interface Me {
  id: string
  email: string
  firstName: string | null
  lastName: string | null
  tenant: string
  roles: string[]
  permissions: string[]
  superAdmin: boolean
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: Me
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
  createdAt: string
}

/** RFC 7807 body produced by GlobalExceptionHandler. */
export interface Problem {
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: Record<string, string>
}
