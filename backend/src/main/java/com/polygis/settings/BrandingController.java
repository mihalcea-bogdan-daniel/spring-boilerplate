package com.polygis.settings;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, per-tenant look and feel. Anonymous on purpose: the login page of acme.example.com must already show
 * Acme's name, colour and logo. Contains nothing sensitive.
 */
@RestController
public class BrandingController {

    public record Branding(String displayName, String primaryColor, String logoUrl,
                           boolean allowSelfRegistration, boolean allowSocialLogin) {
    }

    private final SettingsService settings;

    public BrandingController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/api/auth/branding")
    public Branding branding() {
        Map<String, String> all = settings.all();
        String color = all.get(SettingsService.BRAND_PRIMARY_COLOR);
        return new Branding(all.get("general.displayName"),
                color == null || color.isBlank() ? "emerald" : color,
                all.get(SettingsService.BRAND_LOGO_URL),
                !"false".equals(all.get(SettingsService.ALLOW_SELF_REGISTRATION)),
                !"false".equals(all.get(SettingsService.ALLOW_SOCIAL_LOGIN)));
    }
}
