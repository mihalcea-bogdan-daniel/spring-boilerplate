package com.polygis.tenancy;

import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Plans are plain configuration so pricing changes need no migration:
 * <pre>
 * app.plans.free.max-users: 5
 * app.plans.pro.max-users: 50
 * app.plans.pro.features: [sso, api-keys]
 * </pre>
 *
 * @param plans plan name -> limits. A tenant on an unknown plan is treated as unlimited.
 */
@ConfigurationProperties(prefix = "app")
public record PlanProperties(@DefaultValue Map<String, Plan> plans) {

    /** @param maxUsers maximum number of users, or -1 for unlimited */
    public record Plan(@DefaultValue("-1") int maxUsers, @DefaultValue Set<String> features) {
        public static final Plan UNLIMITED = new Plan(-1, Set.of());
    }
}
