package com.polygis.platform;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.polygis.auth.JwtClaims;
import com.polygis.common.PageResponse;
import com.polygis.common.Sorts;
import com.polygis.platform.TenantDtos.*;

import jakarta.validation.Valid;

/** Super-admin only (also enforced in SecurityConfig for /api/platform/**). */
@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformTenantController {

    private final TenantProvisioningService service;

    public PlatformTenantController(TenantProvisioningService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TenantDto> list(@RequestParam(required = false) String q,
                                        @PageableDefault(size = 20, sort = "createdAt",
                                                direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, Sorts.allow(pageable, "slug", "name", "status", "plan", "createdAt"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantDto create(@Valid @RequestBody CreateTenantRequest req) {
        return service.provision(req);
    }

    @PutMapping("/{id}")
    public TenantDto update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest req) {
        return service.update(id, req);
    }

    /** Plan names configured under app.plans.* (for the tenant editor). */
    @GetMapping("/plans")
    public java.util.Set<String> plans() {
        return service.planNames();
    }

    @GetMapping("/{id}/stats")
    public TenantStatsDto stats(@PathVariable UUID id) {
        return service.stats(id);
    }

    /** Permanently deletes a suspended tenant and its schema. {@code confirm} must repeat the slug. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam String confirm) {
        service.delete(id, confirm);
    }

    public record ImpersonationResponse(String url) {
    }

    /**
     * "Sign in as user": returns a URL on the tenant's own host carrying a 60-second one-time code. Open it in a
     * new tab; the session there is limited to 30 minutes and audited in the tenant's log.
     */
    @PostMapping("/{slug}/impersonate/{userId}")
    public ImpersonationResponse impersonate(@PathVariable String slug, @PathVariable UUID userId,
                                             @AuthenticationPrincipal Jwt jwt) {
        return new ImpersonationResponse(service.impersonate(slug, userId, jwt.getClaimAsString(JwtClaims.EMAIL)));
    }

    @PostMapping("/{id}/migrate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void migrate(@PathVariable UUID id) {
        service.migrate(id);
    }
}
