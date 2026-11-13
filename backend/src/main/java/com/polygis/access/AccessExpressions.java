package com.polygis.access;

import org.springframework.stereotype.Component;

/** For {@code @PreAuthorize("@access.can('write', 'project', #id)")}. */
@Component("access")
public class AccessExpressions {

    private final AccessPolicy policy;

    public AccessExpressions(AccessPolicy policy) {
        this.policy = policy;
    }

    public boolean can(String action, String type, Object id) {
        return id != null && policy.can(action, ResourceRef.of(type, id));
    }
}
