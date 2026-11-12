package com.polygis.platform;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.common.PageResponse;
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
        return service.search(q, pageable);
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

    @PostMapping("/{id}/migrate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void migrate(@PathVariable UUID id) {
        service.migrate(id);
    }
}
