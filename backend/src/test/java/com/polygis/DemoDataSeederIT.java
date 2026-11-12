package com.polygis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.polygis.platform.DemoDataSeeder;
import com.polygis.platform.TenantProvisioningService;
import com.polygis.support.IntegrationTestBase;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.UserAdminService;
import com.polygis.user.UserRepository;

class DemoDataSeederIT extends IntegrationTestBase {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    TenantRegistry registry;
    @Autowired
    UserAdminService users;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    com.polygis.config.AppProperties props;

    @Test
    void seedsDemoTenantsOnceAndTheirAdminsCanSignIn() throws Exception {
        var seeder = new DemoDataSeeder(provisioning, registry, users, userRepository, props);
        seeder.seed();
        seeder.seed(); // idempotent

        assertThat(jdbc.queryForObject("select count(*) from t_acme.users", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from t_globex.users", Integer.class)).isEqualTo(6);
        login(hostOf("acme"), "admin@acme.test", DemoDataSeeder.PASSWORD);
        login(hostOf("globex"), "bob@globex.test", DemoDataSeeder.PASSWORD);
    }
}
