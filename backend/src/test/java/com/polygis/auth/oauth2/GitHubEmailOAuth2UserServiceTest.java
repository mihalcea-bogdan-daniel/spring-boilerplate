package com.polygis.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GitHubEmailOAuth2UserServiceTest {

    @Test
    void prefersPrimaryVerifiedAndIgnoresUnverified() {
        List<Map<String, Object>> emails = List.of(
                Map.of("email", "unverified@x.io", "primary", true, "verified", false),
                Map.of("email", "secondary@x.io", "primary", false, "verified", true),
                Map.of("email", "main@x.io", "primary", true, "verified", true));
        assertThat(GitHubEmailOAuth2UserService.primaryVerified(emails)).contains("main@x.io");
        assertThat(GitHubEmailOAuth2UserService.primaryVerified(emails.subList(0, 2))).contains("secondary@x.io");
        assertThat(GitHubEmailOAuth2UserService.primaryVerified(emails.subList(0, 1))).isEmpty();
        assertThat(GitHubEmailOAuth2UserService.primaryVerified(null)).isEmpty();
    }
}
