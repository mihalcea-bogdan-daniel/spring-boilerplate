package com.polygis.auth.oauth2;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import com.polygis.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties props;

    public OAuth2LoginFailureHandler(AppProperties props) {
        this.props = props;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.info("OAuth2 login failed: {}", exception.getMessage());
        var authRequest = (OAuth2AuthorizationRequest) request.getAttribute(
                HttpCookieOAuth2AuthorizationRequestRepository.REQUEST_ATTR);
        if (authRequest == null) {
            // we do not even know the tenant -> server-rendered Thymeleaf page
            response.sendRedirect("/oauth2/error?reason=state");
            return;
        }
        String host = authRequest.getAttribute(TenantAwareAuthorizationRequestResolver.ATTR_TENANT_HOST);
        response.sendRedirect(UriComponentsBuilder
                .fromUriString(props.security().oauth2().frontendCallback().replace("{host}", host))
                .queryParam("error", "oauth2_failed").build().encode().toUriString());
    }
}
