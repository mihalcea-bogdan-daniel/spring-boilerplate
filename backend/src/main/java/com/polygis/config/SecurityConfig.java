package com.polygis.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.polygis.auth.JwtClaims;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.polygis.apikey.ApiKeyAuthenticationFilter;
import com.polygis.apikey.ApiKeyService;
import com.polygis.auth.AccessTokenRevocation;
import com.polygis.auth.AccessTokenRevocationFilter;
import com.polygis.auth.OtpService;
import com.polygis.auth.oauth2.*;
import com.polygis.tenancy.TenantAccessFilter;
import com.polygis.tenancy.TenantRegistry;

/**
 * Filter order for an API call:
 * <pre>
 * ForwardedHeaderFilter -> TenantResolutionFilter (host) -> [Spring Security:
 *    CorsFilter -> OAuth2 login filters -> BearerTokenAuthenticationFilter -> TenantAccessFilter -> authz]
 * </pre>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties props;

    public SecurityConfig(AppProperties props) {
        this.props = props;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ClientRegistrationRepository clients,
                                            TenantRegistry tenantRegistry,
                                            OAuth2UserProvisioner provisioner,
                                            OtpService otp,
                                            ApiKeyService apiKeys,
                                            AccessTokenRevocation revocation) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // bearer tokens; refresh cookie is SameSite=Strict + path-scoped
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        // API responses, Thymeleaf pages and Swagger UI (which needs inline styles): no inline scripts, no framing
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                        + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .permissionsPolicyHeader(pp -> pp.policy("camera=(), microphone=(), geolocation=()")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**", "/api/oauth2/**", "/api/login/oauth2/**").permitAll()
                        .requestMatchers("/api/docs/**", "/actuator/health/**", "/error").permitAll()
                        // Thymeleaf pages + static assets
                        .requestMatchers(HttpMethod.GET, "/", "/status", "/oauth2/error", "/css/**", "/images/**",
                                "/favicon.ico").permitAll()
                        .requestMatchers("/api/platform/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .oauth2Login(o -> o
                        .authorizationEndpoint(e -> e
                                .baseUri(TenantAwareAuthorizationRequestResolver.BASE_URI)
                                .authorizationRequestResolver(new TenantAwareAuthorizationRequestResolver(clients))
                                .authorizationRequestRepository(
                                        new HttpCookieOAuth2AuthorizationRequestRepository(props)))
                        .redirectionEndpoint(r -> r.baseUri("/api/login/oauth2/code/*"))
                        .userInfoEndpoint(u -> u.userService(new GitHubEmailOAuth2UserService()))
                        .successHandler(new OAuth2LoginSuccessHandler(tenantRegistry, provisioner, otp, props))
                        .failureHandler(new OAuth2LoginFailureHandler(props)))
                .exceptionHandling(e -> e
                        // API clients get 401/403 JSON, never a redirect to a login page
                        .defaultAuthenticationEntryPointFor(new BearerTokenAuthenticationEntryPoint(),
                                AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeys), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new TenantAccessFilter(tenantRegistry, props), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new AccessTokenRevocationFilter(revocation), TenantAccessFilter.class);
        return http.build();
    }

    // JWT signing/verification beans live in JwtConfig

    /** roles -> ROLE_*, perms -> plain authorities (use hasAuthority('user:read') in @PreAuthorize). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList(JwtClaims.ROLES);
            List<String> perms = jwt.getClaimAsStringList(JwtClaims.PERMISSIONS);
            if (roles != null) roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            if (perms != null) perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            return authorities;
        });
        return converter;
    }

    // ------------------------------------------------------------------ misc

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(props.security().password().bcryptStrength());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(props.cors().allowedOriginPatterns());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", props.tenancy().tenantHeader(),
                ApiKeyAuthenticationFilter.HEADER));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
