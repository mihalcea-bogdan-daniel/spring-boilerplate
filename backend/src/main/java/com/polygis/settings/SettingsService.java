package com.polygis.settings;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import org.springframework.transaction.annotation.Transactional;

/** Key/value settings stored in the current tenant's schema. */
@Service
public class SettingsService {

    public static final String ALLOW_SELF_REGISTRATION = "auth.allowSelfRegistration";
    public static final String ALLOW_SOCIAL_LOGIN = "auth.allowSocialLogin";
    public static final String BRAND_PRIMARY_COLOR = "brand.primaryColor";
    public static final String BRAND_LOGO_URL = "brand.logoUrl";

    /** PrimeVue (Aura) palette names a tenant may pick as its primary colour. */
    public static final Set<String> PALETTES = Set.of("emerald", "green", "lime", "red", "orange", "amber", "yellow",
            "teal", "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose", "slate", "zinc");

    private final TenantSettingRepository repository;
    private final AuditService audit;

    public SettingsService(TenantSettingRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new TreeMap<>();
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    private static void validate(Map<String, String> values) {
        String color = values.get(BRAND_PRIMARY_COLOR);
        if (color != null && !PALETTES.contains(color)) {
            throw ApiException.badRequest("invalid_color", "Unknown colour palette: " + color);
        }
        String logo = values.get(BRAND_LOGO_URL);
        if (logo != null && !logo.isBlank() && !(logo.startsWith("https://") && logo.length() <= 500)) {
            throw ApiException.badRequest("invalid_logo", "The logo must be an https:// URL");
        }
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key).map(TenantSetting::getValue).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        validate(values);
        values.forEach((k, v) -> {
            TenantSetting s = repository.findById(k).orElseGet(() -> {
                TenantSetting n = new TenantSetting();
                n.setKey(k);
                return n;
            });
            s.setValue(v);
            repository.save(s);
        });
        audit.record("settings.updated", "settings", null, String.join(", ", values.keySet()));
        return all();
    }
}
