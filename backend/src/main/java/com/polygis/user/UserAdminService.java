package com.polygis.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.audit.AuditService;
import com.polygis.auth.BreachedPasswordChecker;
import com.polygis.auth.TokenService;
import com.polygis.common.ApiException;
import com.polygis.common.PageResponse;
import com.polygis.config.AppProperties;
import com.polygis.mail.MailService;
import com.polygis.tenancy.PlanService;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;
import com.polygis.user.dto.UserDtos.*;

/** User + role administration inside the current tenant (host tenant, or X-Tenant-ID for super-admins). */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final MailService mail;
    private final UserIdentityRepository identities;
    private final AuditService audit;
    private final BreachedPasswordChecker breached;
    private final PlanService plans;
    private final AppProperties props;

    public UserAdminService(UserRepository users, RoleRepository roles, PermissionRepository permissions,
                            PasswordEncoder passwordEncoder, TokenService tokens, MailService mail,
                            AppProperties props, UserIdentityRepository identities,
                            AuditService audit, BreachedPasswordChecker breached, PlanService plans) {
        this.plans = plans;
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.mail = mail;
        this.props = props;
        this.identities = identities;
        this.audit = audit;
        this.breached = breached;
    }

    // ------------------------------------------------------------- users

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, Pageable pageable) {
        return PageResponse.of(users.search(q == null ? "" : q.trim(), pageable), UserDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return UserDto.from(find(id));
    }

    /** Creates a user and, when no password was chosen for them, e-mails an invitation. */
    @Transactional
    public UserDto create(CreateUserRequest req) {
        return create(req, true);
    }

    @Transactional
    public UserDto create(CreateUserRequest req, boolean sendInvite) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("email_taken", "A user with this e-mail already exists");
        }
        plans.assertCanAddUser(users.count());
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        boolean invited = req.password() == null || req.password().isBlank();
        if (invited) {
            // Invited users activate themselves by signing in with an e-mail code (proves inbox ownership).
            u.setStatus(UserStatus.PENDING);
        } else {
            breached.assertNotBreached(req.password());
            u.setPasswordHash(passwordEncoder.encode(req.password()));
            u.setStatus(UserStatus.ACTIVE);
            u.setEmailVerified(true); // the admin chose the credentials and vouches for the address
        }
        u.setRoles(resolveRoles(req.roles() == null || req.roles().isEmpty() ? Set.of(Role.USER) : req.roles()));
        users.save(u);
        audit.record("user.created", "user", u.getId(), u.getEmail() + " roles=" + u.roleNames());
        if (invited && sendInvite) sendInvite(u);
        return UserDto.from(u);
    }

    /** Re-sends the invitation to a user who has not signed in yet. */
    @Transactional(readOnly = true)
    public void resendInvite(UUID id) {
        User u = find(id);
        if (u.getStatus() != UserStatus.PENDING) {
            throw ApiException.badRequest("already_active", "This user has already activated their account");
        }
        sendInvite(u);
    }

    private void sendInvite(User u) {
        TenantInfo tenant = TenantContext.require();
        mail.sendUserInvite(u.getEmail(), u.displayName(), tenant.host(props.tenancy().baseDomain()));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User u = find(id);
        assertWithinCaller(u.permissionCodes(), "change this user");
        long adminsBefore = activeAdmins();
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setStatus(req.status());
        if (req.roles() != null) {
            Set<Role> next = resolveRoles(req.roles());
            // SUPER_ADMIN is not managed through this API: keep it rather than silently stripping it
            u.getRoles().stream().filter(r -> Role.SUPER_ADMIN.equals(r.getName())).forEach(next::add);
            u.setRoles(next);
        }
        assertAdminRemains(adminsBefore);
        if (req.status() == UserStatus.DISABLED) tokens.revokeAll(u.getId());
        audit.record("user.updated", "user", u.getId(), "status=" + u.getStatus() + " roles=" + u.roleNames());
        return UserDto.from(u);
    }

    /**
     * Soft delete: the row stays for audit trails, personal data is blanked, sessions and social links are
     * removed and the e-mail address becomes available again.
     */
    @Transactional
    public void delete(UUID id, UUID actingUserId) {
        if (id.equals(actingUserId) && TenantContext.require().slug().equals(actingTenant())) {
            throw ApiException.badRequest("self_delete", "You cannot delete your own account");
        }
        User u = find(id);
        assertWithinCaller(u.permissionCodes(), "delete this user");
        long adminsBefore = activeAdmins();
        audit.record("user.deleted", "user", u.getId(), u.getEmail());
        tokens.revokeAll(u.getId());
        identities.deleteByUser(u);
        u.setStatus(UserStatus.DISABLED);
        u.setPasswordHash(null);
        u.setFirstName(null);
        u.setLastName(null);
        u.getRoles().clear();
        u.setStatus(UserStatus.DISABLED);
        assertAdminRemains(adminsBefore);
        u.setDeletedAt(java.time.Instant.now());
    }

    /** Home tenant of the caller (differs from the current tenant when a super-admin uses X-Tenant-ID). */
    private static String actingTenant() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString(com.polygis.auth.JwtClaims.TENANT) : null;
    }

    // ------------------------------------------------------------- roles

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roles.findAll().stream().map(RoleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> permissions() {
        return permissions.findAll().stream().map(PermissionDto::from).toList();
    }

    @Transactional
    public RoleDto createRole(RoleRequest req) {
        if (roles.findByName(req.name()).isPresent()) throw ApiException.conflict("role_exists", "Role exists");
        Role r = new Role();
        r.setName(req.name());
        r.setDescription(req.description());
        r.setPermissions(resolvePermissions(req.permissions()));
        roles.save(r);
        audit.record("role.created", "role", r.getId(), r.getName() + " " + req.permissions());
        return RoleDto.from(r);
    }

    @Transactional
    public RoleDto updateRole(UUID id, RoleRequest req) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        assertWithinCaller(permissionsOf(Set.of(r)), "edit this role");
        if (Role.SUPER_ADMIN.equals(r.getName()) || Role.TENANT_ADMIN.equals(r.getName())) {
            // editing the admin role could lock every administrator out of user/role management
            throw ApiException.forbidden("forbidden_role", r.getName() + " has a fixed set of permissions");
        }
        if (!r.isSystemRole()) r.setName(req.name());
        r.setDescription(req.description());
        r.setPermissions(resolvePermissions(req.permissions()));
        audit.record("role.updated", "role", r.getId(), r.getName() + " " + req.permissions());
        return RoleDto.from(r);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        if (r.isSystemRole()) throw ApiException.badRequest("system_role", "System roles cannot be deleted");
        audit.record("role.deleted", "role", r.getId(), r.getName());
        roles.delete(r);
    }

    // ------------------------------------------------------------- privilege guard rails

    /**
     * Permissions of the caller, or null for internal calls without a security context (provisioning, seeding).
     * Users, roles and API keys are all limited to what the caller already holds.
     */
    private static Set<String> callerPermissions() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Set<String> perms = new HashSet<>();
        auth.getAuthorities().forEach(a -> perms.add(a.getAuthority()));
        return perms;
    }

    private static Set<String> permissionsOf(java.util.Collection<Role> roles) {
        Set<String> perms = new HashSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> perms.add(p.getCode())));
        return perms;
    }

    /** You cannot grant (or take away) permissions you do not hold yourself. */
    private static void assertWithinCaller(Set<String> permissions, String what) {
        Set<String> caller = callerPermissions();
        if (caller != null && !caller.containsAll(permissions)) {
            Set<String> missing = new java.util.TreeSet<>(permissions);
            missing.removeAll(caller);
            throw ApiException.forbidden("permission_escalation",
                    "You cannot " + what + " because it involves permissions you do not have: " + missing);
        }
    }

    private String adminRole() {
        return TenantContext.require().slug().equals(props.tenancy().platformTenant()) ? Role.SUPER_ADMIN : Role.TENANT_ADMIN;
    }

    private long activeAdmins() {
        return users.countActiveWithRole(adminRole());
    }

    /**
     * Every tenant keeps at least one active administrator (the platform: one super-admin). Only changes that
     * take the count from "some" to zero are refused, so a fresh tenant whose invited admin has not signed in
     * yet can still be managed.
     */
    private void assertAdminRemains(long before) {
        users.flush();
        String role = adminRole();
        if (before > 0 && users.countActiveWithRole(role) == 0) {
            throw ApiException.conflict("last_admin", "This would leave the workspace without an active " + role);
        }
    }

    // ------------------------------------------------------------- helpers

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    private Set<Role> resolveRoles(Set<String> names) {
        if (names.contains(Role.SUPER_ADMIN)) {
            throw ApiException.forbidden("forbidden_role", "SUPER_ADMIN cannot be assigned here");
        }
        List<Role> found = roles.findByNameIn(names);
        if (found.size() != names.size()) throw ApiException.badRequest("unknown_role", "Unknown role in " + names);
        assertWithinCaller(permissionsOf(found), "assign these roles");
        return new HashSet<>(found);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        assertWithinCaller(codes, "grant these permissions");
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size()) throw ApiException.badRequest("unknown_permission", "Unknown permission");
        return new HashSet<>(found);
    }
}
