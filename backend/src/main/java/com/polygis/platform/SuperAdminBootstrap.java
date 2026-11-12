package com.polygis.platform;

import java.util.HashSet;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.polygis.config.AppProperties;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the platform tenant has the SUPER_ADMIN role (with every permission + tenant:manage) and, on first
 * boot, a super-admin account from SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD.
 */
@Slf4j
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private final AppProperties props;
    private final TenantRegistry registry;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;

    public SuperAdminBootstrap(AppProperties props, TenantRegistry registry, UserRepository users,
                               RoleRepository roles, PermissionRepository permissions,
                               PasswordEncoder passwordEncoder, TransactionTemplate tx) {
        this.props = props;
        this.registry = registry;
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        var platform = registry.requireBySlug(props.tenancy().platformTenant());
        TenantContext.runAs(platform, () -> tx.executeWithoutResult(s -> {
            Permission manage = permissions.findByCode("tenant:manage").orElseGet(() -> {
                Permission p = new Permission();
                p.setCode("tenant:manage");
                p.setDescription("Provision and manage tenants");
                return permissions.save(p);
            });
            Role superAdmin = roles.findByName(Role.SUPER_ADMIN).orElseGet(() -> {
                Role r = new Role();
                r.setName(Role.SUPER_ADMIN);
                r.setDescription("Platform operator: manages every tenant");
                r.setSystemRole(true);
                return r;
            });
            var all = new HashSet<>(permissions.findAll());
            all.add(manage);
            superAdmin.setPermissions(all);
            roles.save(superAdmin);

            var bootstrap = props.bootstrap();
            if (bootstrap == null || !StringUtils.hasText(bootstrap.superAdminEmail())
                    || !StringUtils.hasText(bootstrap.superAdminPassword())) {
                return;
            }
            if (users.existsByEmailIgnoreCase(bootstrap.superAdminEmail())) return;
            User u = new User();
            u.setEmail(bootstrap.superAdminEmail().toLowerCase());
            u.setFirstName("Super");
            u.setLastName("Admin");
            u.setPasswordHash(passwordEncoder.encode(bootstrap.superAdminPassword()));
            u.setStatus(UserStatus.ACTIVE);
            u.setEmailVerified(true);
            u.getRoles().add(superAdmin);
            users.save(u);
            log.warn("Created bootstrap super-admin {} - change its password", u.getEmail());
        }));
    }
}
