package com.polygis.apikey;

import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** A machine caller authenticated by a tenant API key; authorities are the key's permission codes. */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final UUID keyId;
    private final String keyName;

    public ApiKeyAuthentication(ApiKey key) {
        super(key.permissionSet().stream().map(SimpleGrantedAuthority::new).toList());
        this.keyId = key.getId();
        this.keyName = key.getName();
        setAuthenticated(true);
    }

    public UUID getKeyId() {
        return keyId;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return getName();
    }

    /** Appears as the actor in the audit log. */
    @Override
    public String getName() {
        return "api-key:" + keyName;
    }
}
