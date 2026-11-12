package com.polygis.auth;

public final class JwtClaims {
    public static final String TENANT = "tenant";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "perms";
    public static final String TOKEN_TYPE = "typ";
    public static final String ACCESS = "access";

    private JwtClaims() {
    }
}
