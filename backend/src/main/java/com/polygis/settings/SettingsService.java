package com.polygis.settings;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Key/value settings stored in the current tenant's schema. */
@Service
public class SettingsService {

    public static final String ALLOW_SELF_REGISTRATION = "auth.allowSelfRegistration";
    public static final String ALLOW_SOCIAL_LOGIN = "auth.allowSocialLogin";

    private final TenantSettingRepository repository;

    public SettingsService(TenantSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new TreeMap<>();
        repository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findById(key).map(TenantSetting::getValue).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        values.forEach((k, v) -> {
            TenantSetting s = repository.findById(k).orElseGet(() -> {
                TenantSetting n = new TenantSetting();
                n.setKey(k);
                return n;
            });
            s.setValue(v);
            repository.save(s);
        });
        return all();
    }
}
