package com.polygis.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.polygis.config.AppProperties;

class TenantFiltersTest {

    private static final TenantInfo PLATFORM = tenant("platform", "admin.example.com");
    private static final TenantInfo ACME = tenant("acme", null);
    private static final TenantInfo GLOBEX = tenant("globex", "portal.globex.io");

    private TenantRegistry registry;
    private AppProperties props;

    @BeforeEach
    void setUp() {
        registry = mock(TenantRegistry.class);
        when(registry.findBySlug("platform")).thenReturn(Optional.of(PLATFORM));
        when(registry.findBySlug("acme")).thenReturn(Optional.of(ACME));
        when(registry.findBySlug("globex")).thenReturn(Optional.of(GLOBEX));
        when(registry.findByDomain("admin.example.com")).thenReturn(Optional.of(PLATFORM));
        when(registry.findByDomain("portal.globex.io")).thenReturn(Optional.of(GLOBEX));
        props = new AppProperties(
                new AppProperties.Tenancy("example.com", "platform", "admin.example.com", "X-Tenant-ID", false,
                        Set.of("www", "api")),
                null, null, null, new AppProperties.Cors(List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void resolvesSubdomainCustomDomainAndRejectsUnknownOrReserved() {
        var resolver = new HostTenantResolver(registry, props);
        assertThat(resolver.resolve(req("acme.example.com"))).contains(ACME);
        assertThat(resolver.resolve(req("portal.globex.io"))).contains(GLOBEX);
        assertThat(resolver.resolve(req("admin.example.com"))).contains(PLATFORM);
        assertThat(resolver.resolve(req("www.example.com"))).isEmpty();
        assertThat(resolver.resolve(req("x.acme.example.com"))).isEmpty();
        assertThat(resolver.resolve(req("evil.com"))).isEmpty();
    }

    @Test
    void unknownHostIs404() throws Exception {
        var filter = new TenantResolutionFilter(new HostTenantResolver(registry, props));
        var res = new MockHttpServletResponse();
        filter.doFilter(req("nope.example.com"), res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(404);
    }

    @Test
    void superAdminSwitchesTenantWithHeader() throws Exception {
        TenantContext.set(PLATFORM);
        authenticate("platform", "SUPER_ADMIN");
        var request = req("admin.example.com");
        request.addHeader("X-Tenant-ID", "acme");

        AtomicReference<TenantInfo> seen = new AtomicReference<>();
        var res = new MockHttpServletResponse();
        new TenantAccessFilter(registry, props).doFilter(request, res,
                (rq, rs) -> seen.set(TenantContext.require()));

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isEqualTo(ACME);
    }

    @Test
    void regularUserCannotUseHeaderOrForeignHost() throws Exception {
        TenantContext.set(ACME);
        authenticate("acme", "TENANT_ADMIN");
        var request = req("acme.example.com");
        request.addHeader("X-Tenant-ID", "globex");
        var res = new MockHttpServletResponse();
        new TenantAccessFilter(registry, props).doFilter(request, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(403);

        TenantContext.set(GLOBEX); // acme token replayed on globex host
        var res2 = new MockHttpServletResponse();
        new TenantAccessFilter(registry, props).doFilter(req("portal.globex.io"), res2, new MockFilterChain());
        assertThat(res2.getStatus()).isEqualTo(403);
    }

    @Test
    void superAdminRoleFromNonPlatformTenantIsIgnored() throws Exception {
        TenantContext.set(ACME);
        authenticate("acme", "SUPER_ADMIN"); // forged/mis-assigned role in a tenant schema
        var request = req("acme.example.com");
        request.addHeader("X-Tenant-ID", "globex");
        var res = new MockHttpServletResponse();
        new TenantAccessFilter(registry, props).doFilter(request, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    private static MockHttpServletRequest req(String host) {
        var r = new MockHttpServletRequest("GET", "/api/admin/users");
        r.setServerName(host);
        return r;
    }

    private static void authenticate(String tenant, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .claim("tenant", tenant).issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plus(Duration.ofMinutes(5))).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static TenantInfo tenant(String slug, String domain) {
        return new TenantInfo(UUID.randomUUID(), slug, TenantSchemas.schemaFor(slug), domain, TenantStatus.ACTIVE);
    }
}
