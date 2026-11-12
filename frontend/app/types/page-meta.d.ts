declare module '#app' {
  interface PageMeta {
    /** Page is reachable without a session (login, register...). */
    public?: boolean
    /** Authority required (JWT "perms" claim), e.g. 'user:read'. */
    permission?: string
    /** Only platform super-admins. */
    superAdminOnly?: boolean
  }
}

export {}
