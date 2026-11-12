package com.polygis.settings;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('settings:read')")
    public Map<String, String> get() {
        return settings.all();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings:write')")
    public Map<String, String> update(@RequestBody Map<String, String> values) {
        return settings.update(values);
    }
}
