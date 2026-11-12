package com.polygis.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.auth.TokenService;
import com.polygis.common.ApiException;
import com.polygis.common.PageResponse;
import com.polygis.user.dto.UserDtos.*;

/** User + role administration inside the current tenant (host tenant, or X-Tenant-ID for super-admins). */
@Service
public class UserAdminService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public UserAdminService(UserRepository users, RoleRepository roles, PermissionRepository permissions,
                            PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
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

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("email_taken", "A user with this e-mail already exists");
        }
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        if (req.password() != null) u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true); // created by an admin; they vouch for the address
        u.setRoles(resolveRoles(req.roles() == null || req.roles().isEmpty() ? Set.of(Role.USER) : req.roles()));
        return UserDto.from(users.save(u));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req) {
        User u = find(id);
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setStatus(req.status());
        if (req.roles() != null) u.setRoles(resolveRoles(req.roles()));
        if (req.status() == UserStatus.DISABLED) tokens.revokeAll(u.getId());
        return UserDto.from(u);
    }

    @Transactional
    public void delete(UUID id) {
        users.delete(find(id));
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
        return RoleDto.from(roles.save(r));
    }

    @Transactional
    public RoleDto updateRole(UUID id, RoleRequest req) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        if (!r.isSystemRole()) r.setName(req.name());
        r.setDescription(req.description());
        r.setPermissions(resolvePermissions(req.permissions()));
        return RoleDto.from(r);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role r = roles.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        if (r.isSystemRole()) throw ApiException.badRequest("system_role", "System roles cannot be deleted");
        roles.delete(r);
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
        return new HashSet<>(found);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size()) throw ApiException.badRequest("unknown_permission", "Unknown permission");
        return new HashSet<>(found);
    }
}
