package com.polygis.apikey;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin/api-keys")
@PreAuthorize("hasAuthority('apikey:manage')")
public class ApiKeyController {

    public record ApiKeyDto(UUID id, String name, String keyPrefix, Set<String> permissions, String createdBy,
                            Instant createdAt, Instant lastUsedAt) {
        static ApiKeyDto from(ApiKey k) {
            return new ApiKeyDto(k.getId(), k.getName(), k.getKeyPrefix(), k.permissionSet(), k.getCreatedBy(),
                    k.getCreatedAt(), k.getLastUsedAt());
        }
    }

    /** @param key the full secret - returned exactly once, at creation */
    public record CreatedApiKeyDto(ApiKeyDto apiKey, String key) {
    }

    public record CreateApiKeyRequest(@NotBlank @Size(max = 100) String name, @NotEmpty Set<String> permissions) {
    }

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApiKeyDto> list() {
        return service.list().stream().map(ApiKeyDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApiKeyDto create(@Valid @RequestBody CreateApiKeyRequest req, Authentication authentication) {
        com.polygis.auth.Impersonation.reject(authentication);
        var created = service.create(req.name(), req.permissions(), authentication);
        return new CreatedApiKeyDto(ApiKeyDto.from(created.key()), created.rawKey());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, Authentication authentication) {
        com.polygis.auth.Impersonation.reject(authentication);
        service.revoke(id);
    }
}
