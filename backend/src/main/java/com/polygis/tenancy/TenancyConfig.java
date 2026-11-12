package com.polygis.tenancy;

import javax.sql.DataSource;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.polygis.config.AppProperties;

/**
 * Wires schema-per-tenant into Hibernate and the servlet pipeline.
 * <p>Ordering matters:
 * <ol>
 *   <li>{@link TenantSchemaMigrator#migrateAll()} runs while the customizer bean is created, i.e. before the
 *       EntityManagerFactory exists - no request can ever see an un-migrated schema;</li>
 *   <li>{@link TenantResolutionFilter} runs before Spring Security (order HIGHEST_PRECEDENCE + 10) so the
 *       host tenant is known during authentication (login, OAuth2 user lookup...).</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class TenancyConfig {

    @Bean
    TenantSchemaMigrator tenantSchemaMigrator(DataSource dataSource, AppProperties props) {
        TenantSchemaMigrator migrator = new TenantSchemaMigrator(dataSource, props);
        migrator.migrateAll();
        return migrator;
    }

    @Bean
    SchemaMultiTenantConnectionProvider multiTenantConnectionProvider(DataSource dataSource) {
        return new SchemaMultiTenantConnectionProvider(dataSource);
    }

    @Bean
    TenantIdentifierResolver tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    HibernatePropertiesCustomizer multiTenancyCustomizer(TenantSchemaMigrator migrator,
                                                         SchemaMultiTenantConnectionProvider connectionProvider,
                                                         TenantIdentifierResolver identifierResolver) {
        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, identifierResolver);
        };
    }

    @Bean
    FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilter(HostTenantResolver resolver,
                                                                           TenantRegistry registry,
                                                                           TenantSchemaMigrator migrator) {
        registry.reload(); // migrations done -> warm the cache before the first request
        var reg = new FilterRegistrationBean<>(new TenantResolutionFilter(resolver));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
