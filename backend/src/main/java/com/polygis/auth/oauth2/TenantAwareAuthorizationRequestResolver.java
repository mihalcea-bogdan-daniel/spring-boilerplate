package com.polygis.auth.oauth2;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Stamps the tenant (and the SPA path to return to) onto the authorization request. They travel inside the
 * signed cookie, so the callback knows the tenant even when it arrives on a central callback host.
 * Entry point: {@code GET https://acme.example.com/api/oauth2/authorization/google?redirect=/dashboard}
 */
public class TenantAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String ATTR_TENANT = "tenant";
    public static final String ATTR_TENANT_HOST = "tenant_host";
    public static final String ATTR_REDIRECT = "redirect";
    public static final String BASE_URI = "/api/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public TenantAwareAuthorizationRequestResolver(ClientRegistrationRepository clients) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customize(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest req, HttpServletRequest http) {
        if (req == null) return null;
        TenantInfo tenant = TenantContext.require();
        Map<String, Object> attrs = new HashMap<>(req.getAttributes());
        attrs.put(ATTR_TENANT, tenant.slug());
        attrs.put(ATTR_TENANT_HOST, http.getServerName());
        attrs.put(ATTR_REDIRECT, safeRedirect(http.getParameter("redirect")));
        return OAuth2AuthorizationRequest.from(req).attributes(a -> a.putAll(attrs)).build();
    }

    /** Only same-origin relative paths: blocks open redirects such as //evil.com or https://evil.com. */
    static String safeRedirect(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")) return "/";
        return value;
    }
}
