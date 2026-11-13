package com.polygis.org;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.org.OrgDtos.*;

import jakarta.validation.Valid;

/** Restructuring: units and the rank catalogue. Tenant admins only. */
@RestController
@RequestMapping("/api/admin/org")
@PreAuthorize("hasAuthority('org:manage')")
public class OrgAdminController {

    private final OrgService service;

    public OrgAdminController(OrgService service) {
        this.service = service;
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitDto createUnit(@Valid @RequestBody UnitRequest req) {
        return service.createUnit(req);
    }

    @PutMapping("/units/{id}")
    public UnitDto updateUnit(@PathVariable UUID id, @Valid @RequestBody UnitRequest req) {
        return service.updateUnit(id, req);
    }

    @DeleteMapping("/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnit(@PathVariable UUID id) {
        service.deleteUnit(id);
    }

    @PostMapping("/ranks")
    @ResponseStatus(HttpStatus.CREATED)
    public RankDto createRank(@Valid @RequestBody RankRequest req) {
        return service.createRank(req);
    }

    @PutMapping("/ranks/{id}")
    public RankDto updateRank(@PathVariable UUID id, @Valid @RequestBody RankRequest req) {
        return service.updateRank(id, req);
    }

    @DeleteMapping("/ranks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRank(@PathVariable UUID id) {
        service.deleteRank(id);
    }
}
