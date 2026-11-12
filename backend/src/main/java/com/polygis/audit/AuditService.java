package com.polygis.audit;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.polygis.auth.JwtClaims;

import lombok.extern.slf4j.Slf4j;

/**
 * Append-only audit trail stored in the <b>current</b> tenant schema. When a super-admin acts through
 * X-Tenant-ID the event lands in the managed tenant's log with {@code actor_tenant = platform}, so tenant
 * admins can see what the platform operator did to their workspace.
 * <p>Joins the caller's transaction: an action that rolls back leaves no audit entry.
 */
@Slf4j
@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Records an action performed by the authenticated caller (if any). */
    @Transactional
    public void record(String action, String targetType, Object targetId, String details) {
        AuditEvent e = base(action, targetType, targetId, details);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            e.setActorUserId(UUID.fromString(jwt.getToken().getSubject()));
            String email = jwt.getToken().getClaimAsString(JwtClaims.EMAIL);
            String impersonator = jwt.getToken().getClaimAsString(JwtClaims.ACTOR);
            // impersonated sessions are always attributable to the operator behind them
            e.setActorEmail(impersonator == null ? email : email + " (via " + impersonator + ")");
            e.setActorTenant(jwt.getToken().getClaimAsString(JwtClaims.TENANT));
        } else if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            e.setActorEmail(auth.getName()); // machine callers, e.g. "api-key:<name>"
        }
        repository.save(e);
    }

    /** Records an action for flows where nobody is authenticated yet (login, password reset...). */
    @Transactional
    public void recordAs(UUID actorUserId, String actorEmail, String action, String details) {
        AuditEvent e = base(action, "user", actorUserId, details);
        e.setActorUserId(actorUserId);
        e.setActorEmail(actorEmail);
        repository.save(e);
    }

    private AuditEvent base(String action, String targetType, Object targetId, String details) {
        AuditEvent e = new AuditEvent();
        e.setAction(action);
        e.setTargetType(targetType);
        e.setTargetId(targetId == null ? null : targetId.toString());
        e.setDetails(details);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            e.setIpAddress(attrs.getRequest().getRemoteAddr());
        }
        return e;
    }
}
