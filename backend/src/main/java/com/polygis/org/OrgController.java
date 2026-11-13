package com.polygis.org;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.org.OrgDtos.*;

import jakarta.validation.Valid;

/**
 * The organisation as everyone sees it, plus member management. Member changes are open to {@code org:manage}
 * and to leaders of the unit (ranks strictly below their own); the service enforces the second case.
 */
@RestController
@RequestMapping("/api/org")
public class OrgController {

    private final OrgService service;

    public OrgController(OrgService service) {
        this.service = service;
    }

    @GetMapping("/chart")
    @PreAuthorize("hasAuthority('org:read')")
    public ChartNode chart() {
        return service.chart(OrgAccess.currentUserId());
    }

    @GetMapping("/ranks")
    @PreAuthorize("hasAuthority('org:read')")
    public List<RankDto> ranks() {
        return service.ranks();
    }

    @GetMapping("/people")
    @PreAuthorize("hasAuthority('org:manage') or @org.leadsAny()")
    public List<PersonDto> people(@RequestParam(required = false) String q) {
        return service.people(q);
    }

    @PostMapping("/units/{unitId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('org:manage') or @org.leads(#unitId)")
    public void addMember(@PathVariable UUID unitId, @Valid @RequestBody MemberRequest req) {
        service.addMember(OrgAccess.requireUserId(), OrgAccess.hasAuthority(OrgAccess.MANAGE), unitId, req);
    }

    @PutMapping("/units/{unitId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('org:manage') or @org.leads(#unitId)")
    public void changeRank(@PathVariable UUID unitId, @PathVariable UUID userId,
                           @Valid @RequestBody RankChangeRequest req) {
        service.changeRank(OrgAccess.requireUserId(), OrgAccess.hasAuthority(OrgAccess.MANAGE), unitId, userId,
                req.rankId());
    }

    @DeleteMapping("/units/{unitId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('org:manage') or @org.leads(#unitId)")
    public void removeMember(@PathVariable UUID unitId, @PathVariable UUID userId) {
        service.removeMember(OrgAccess.requireUserId(), OrgAccess.hasAuthority(OrgAccess.MANAGE), unitId, userId);
    }
}
