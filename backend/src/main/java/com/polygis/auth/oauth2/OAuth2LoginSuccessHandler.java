package com.polygis.auth.oauth2;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.auth.OtpService;
import com.polygis.common.ApiException;
import com.polygis.config.AppProperties;
import com.polygis.tenancy.TenantContext;
import com.polygis.tenancy.TenantInfo;
import com.polygis.tenancy.TenantRegistry;
import com.polygis.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After the provider callback: resolve the tenant from the signed state cookie, link/create the user in that
 * tenant's schema and redirect the browser to the SPA with a 60s single-use code (never tokens in URLs).
 */
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TenantRegistry registry;
    private final OAuth2UserProvisioner provisioner;
    private final OtpService otp;
    private final AppProperties props;

    public OAuth2LoginSuccessHandler(TenantRegistry registry, OAuth2UserProvisioner provisioner, OtpService otp,
                                     AppProperties props) {
        this.registry = registry;
        this.provisioner = provisioner;
        this.otp = otp;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityContextHolder.clearContext(); // we are stateless: the SPA gets our own JWT instead
        var authRequest = (OAuth2AuthorizationRequest) request.getAttribute(
                HttpCookieOAuth2AuthorizationRequestRepository.REQUEST_ATTR);
        if (authRequest == null) {
            response.sendRedirect("/oauth2/error?reason=state");
            return;
        }
        String slug = authRequest.getAttribute(TenantAwareAuthorizationRequestResolver.ATTR_TENANT);
        String host = authRequest.getAttribute(TenantAwareAuthorizationRequestResolver.ATTR_TENANT_HOST);
        String redirect = authRequest.getAttribute(TenantAwareAuthorizationRequestResolver.ATTR_REDIRECT);
        TenantInfo tenant = registry.requireBySlug(slug);

        var token = (OAuth2AuthenticationToken) authentication;
        var profile = OAuth2UserProvisioner.ProviderProfile.from(token.getAuthorizedClientRegistrationId(),
                token.getPrincipal().getAttributes());

        UriComponentsBuilder target = UriComponentsBuilder.fromUriString(
                props.security().oauth2().frontendCallback().replace("{host}", host));
        try {
            String code = TenantContext.callAs(tenant, () -> {
                User user = provisioner.findOrCreate(profile);
                return otp.issueExchangeCode(user.getId());
            });
            target.queryParam("code", code).queryParam("redirect", redirect);
        } catch (ApiException e) {
            target.queryParam("error", e.getCode());
        }
        response.sendRedirect(target.build().encode().toUriString());
    }
}
