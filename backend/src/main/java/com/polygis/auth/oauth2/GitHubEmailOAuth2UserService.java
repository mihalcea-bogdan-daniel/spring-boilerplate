package com.polygis.auth.oauth2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * GitHub's /user endpoint only returns the <i>public</i> e-mail, which most users hide. With the
 * {@code user:email} scope we can ask /user/emails for the primary verified address instead.
 * Other providers pass through untouched.
 */
@Slf4j
public class GitHubEmailOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final RestClient rest;

    public GitHubEmailOAuth2UserService() {
        this(new DefaultOAuth2UserService(), RestClient.create());
    }

    GitHubEmailOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, RestClient rest) {
        this.delegate = delegate;
        this.rest = rest;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = delegate.loadUser(request);
        if (!"github".equals(request.getClientRegistration().getRegistrationId())) return user;

        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put("email_verified", false); // the public profile e-mail carries no verification info
        try {
            List<Map<String, Object>> emails = rest.get().uri(EMAILS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().getTokenValue())
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            primaryVerified(emails).ifPresent(email -> {
                attributes.put("email", email);
                attributes.put("email_verified", true);
            });
        } catch (RestClientException e) {
            log.warn("Could not load GitHub e-mails: {}", e.getMessage());
        }
        String nameAttribute = request.getClientRegistration().getProviderDetails().getUserInfoEndpoint()
                .getUserNameAttributeName();
        return new DefaultOAuth2User(user.getAuthorities(), attributes, nameAttribute);
    }

    /** Primary verified address first, otherwise any verified one. Unverified addresses are never used. */
    static Optional<String> primaryVerified(List<Map<String, Object>> emails) {
        if (emails == null) return Optional.empty();
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("verified")) && e.get("email") != null)
                .sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.get("primary")),
                        Boolean.TRUE.equals(a.get("primary"))))
                .map(e -> e.get("email").toString())
                .findFirst();
    }
}
