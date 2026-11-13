package com.polygis.org;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.polygis.common.ApiException;

/**
 * Hierarchy checks for {@code @PreAuthorize}, e.g. {@code @PreAuthorize("@org.supervises(#userId)")}.
 * Only people (JWT sessions) have a place in the organisation; API keys never pass these checks.
 */
@Component("org")
public class OrgAccess {

    public static final String MANAGE = "org:manage";

    private final OrgService service;

    public OrgAccess(OrgService service) {
        this.service = service;
    }

    /** The signed-in person's id, or null for machine callers. */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? UUID.fromString(jwt.getToken().getSubject()) : null;
    }

    public static UUID requireUserId() {
        UUID id = currentUserId();
        if (id == null) throw ApiException.forbidden("person_required", "Only people can do this");
        return id;
    }

    public static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    public boolean supervises(UUID userId) {
        UUID me = currentUserId();
        return me != null && service.supervises(me, userId);
    }

    /** True when the caller leads at least one unit (holds a supervising rank somewhere). */
    public boolean leadsAny() {
        UUID me = currentUserId();
        return me != null && service.leadsAny(me);
    }

    public boolean leads(UUID unitId) {
        UUID me = currentUserId();
        return me != null && service.leadLevel(me, unitId) != null;
    }
}
