package com.polygis.platform;

import java.time.Instant;
import java.util.UUID;

import com.polygis.tenancy.Tenant;
import com.polygis.tenancy.TenantStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class TenantDtos {

    private TenantDtos() {
    }

    public record TenantDto(UUID id, String slug, String name, String schemaName, String domain,
                            TenantStatus status, Instant createdAt) {
        public static TenantDto from(Tenant t) {
            return new TenantDto(t.getId(), t.getSlug(), t.getName(), t.getSchemaName(), t.getDomain(),
                    t.getStatus(), t.getCreatedAt());
        }
    }

    public record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,40}[a-z0-9]$",
                    message = "lowercase letters, digits and dashes (3-42 chars)") String slug,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String domain,
            @NotBlank @Email String adminEmail,
            @Size(max = 100) String adminFirstName,
            @Size(max = 100) String adminLastName) {
    }

    public record UpdateTenantRequest(@NotBlank @Size(max = 255) String name,
                                      @Size(max = 255) String domain,
                                      @NotNull TenantStatus status) {
    }
}
