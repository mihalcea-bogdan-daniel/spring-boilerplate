package com.polygis.platform;

import java.util.List;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.polygis.config.AppProperties;
import com.polygis.org.DemoOrganisation;
import com.polygis.platform.TenantDtos.CreateTenantRequest;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.Role;
import com.polygis.user.UserAdminService;
import com.polygis.user.UserRepository;
import com.polygis.user.dto.UserDtos.CreateUserRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Demo tenants and users for local development. Never active unless explicitly requested:
 * <pre>./gradlew bootRun --args='--app.seed=true'</pre>
 * Idempotent: existing tenants and users are left alone.
 */
@Slf4j
@Component
@Order(100) // after SuperAdminBootstrap
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String PASSWORD = "Demo-Passw0rd!";
    private static final List<String> TENANTS = List.of("acme", "globex");
    private static final List<String> PEOPLE = List.of("alice", "bob", "carol", "dave");
    /** acme also gets a whole organisation: these people fill its units (see DemoOrganisation). */
    private static final List<String> ORG_PEOPLE = List.of("erin", "frank", "gina", "hana", "ivan");

    private final TenantProvisioningService provisioning;
    private final TenantRegistry registry;
    private final UserAdminService users;
    private final UserRepository userRepository;
    private final String platformTenant;
    private final DemoOrganisation organisation;

    public DemoDataSeeder(TenantProvisioningService provisioning, TenantRegistry registry, UserAdminService users,
                          UserRepository userRepository, AppProperties props, DemoOrganisation organisation) {
        this.organisation = organisation;
        this.platformTenant = props.tenancy().platformTenant();
        this.provisioning = provisioning;
        this.registry = registry;
        this.users = users;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        for (String slug : TENANTS) {
            if (registry.findBySlug(slug).isEmpty()) {
                String name = Character.toUpperCase(slug.charAt(0)) + slug.substring(1) + " Inc.";
                // provisioning is a platform action (its audit entry lands in the platform schema)
                TenantContext.runAs(registry.requireBySlug(platformTenant), () -> {
                    var tenant = provisioning.provision(
                            new CreateTenantRequest(slug, name, null, "owner@" + slug + ".test", "Olivia", "Owner"));
                    // demo tenants hold more users than the free plan allows
                    provisioning.update(tenant.id(), new TenantDtos.UpdateTenantRequest(name, null,
                            com.polygis.tenancy.TenantStatus.ACTIVE, "pro"));
                });
            }
            TenantContext.runAs(registry.requireBySlug(slug), () -> {
                createIfMissing("admin@" + slug + ".test", "Adam", "Admin", Role.TENANT_ADMIN);
                PEOPLE.forEach(p -> createIfMissing(p + "@" + slug + ".test",
                        Character.toUpperCase(p.charAt(0)) + p.substring(1), "Demo", Role.USER));
                if ("acme".equals(slug)) {
                    ORG_PEOPLE.forEach(p -> createIfMissing(p + "@" + slug + ".test",
                            Character.toUpperCase(p.charAt(0)) + p.substring(1), "Demo", Role.USER));
                    organisation.seed(slug + ".test");
                }
            });
        }
        log.warn("Demo data ready: tenants {} - sign in as admin@<tenant>.test / {}", TENANTS, PASSWORD);
    }

    private void createIfMissing(String email, String firstName, String lastName, String role) {
        if (userRepository.existsByEmailIgnoreCase(email)) return;
        users.create(new CreateUserRequest(email, firstName, lastName, PASSWORD, Set.of(role)), false);
    }
}
