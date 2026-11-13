package com.polygis.org;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.audit.AuditService;
import com.polygis.auth.OtpService;
import com.polygis.common.ApiException;
import com.polygis.config.AppProperties;
import com.polygis.tenancy.TenantContext;
import com.polygis.user.Role;
import com.polygis.user.User;
import com.polygis.user.UserRepository;
import com.polygis.user.UserStatus;

/**
 * "Sign in as" for supervisors. Reuses the platform impersonation flow (one-time code, 30-minute access-only
 * token with the {@code act} claim, banner in the UI) and adds the hierarchy guards.
 */
@Service
public class TeamService {

    private final OrgService org;
    private final UserRepository users;
    private final OtpService otp;
    private final AuditService audit;
    private final AppProperties props;

    public TeamService(OrgService org, UserRepository users, OtpService otp, AuditService audit, AppProperties props) {
        this.org = org;
        this.users = users;
        this.otp = otp;
        this.audit = audit;
        this.props = props;
    }

    /**
     * @return URL on this tenant's host that starts the impersonated session in a new tab
     * @throws ApiException 403 unless the actor supervises the target, the target does not supervise the actor,
     *                      and the target holds no permission the actor lacks
     */
    @Transactional
    public String impersonate(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) throw ApiException.badRequest("self", "You are already signed in as yourself");
        User actor = users.findById(actorId).orElseThrow(() -> ApiException.notFound("User"));
        User target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("User"));
        if (target.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.badRequest("target_inactive", "Only active people can be signed in as");
        }
        if (!org.supervises(actorId, targetId)) {
            throw ApiException.forbidden("not_supervised", "You do not supervise this person");
        }
        if (org.supervises(targetId, actorId)) {
            throw ApiException.forbidden("mutual_supervision", "This person also supervises you");
        }
        Set<String> actorPermissions = actor.permissionCodes();
        if (target.roleNames().contains(Role.SUPER_ADMIN) || !actorPermissions.containsAll(target.permissionCodes())) {
            throw ApiException.forbidden("privilege_escalation", "This person holds permissions you don't have");
        }
        audit.record("org.impersonation.started", "user", targetId, actor.getEmail());
        String code = otp.issueImpersonationCode(targetId, actor.getEmail());
        return UriComponentsBuilder
                .fromUriString(props.security().oauth2().frontendCallback()
                        .replace("{host}", TenantContext.require().host(props.tenancy().baseDomain())))
                .queryParam("impersonate", code).build().encode().toUriString();
    }
}
