package com.polygis.org;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.polygis.audit.AuditController.AuditEventDto;
import com.polygis.audit.AuditEventRepository;
import com.polygis.auth.Impersonation;
import com.polygis.common.PageResponse;
import com.polygis.common.Sorts;
import com.polygis.org.OrgDtos.SubordinateDto;

/** The people the caller supervises: who they are, what they did, and "sign in as". */
@RestController
@RequestMapping("/api/me")
public class TeamController {

    public record ImpersonationResponse(String url) {
    }

    private final OrgService org;
    private final TeamService team;
    private final AuditEventRepository events;

    public TeamController(OrgService org, TeamService team, AuditEventRepository events) {
        this.org = org;
        this.team = team;
        this.events = events;
    }

    @GetMapping("/team")
    public List<SubordinateDto> team() {
        return org.team(OrgAccess.requireUserId());
    }

    @GetMapping("/team/activity")
    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> activity(@PageableDefault(size = 25, sort = "occurredAt",
            direction = Sort.Direction.DESC) Pageable pageable) {
        var ids = org.subordinateIds(OrgAccess.requireUserId());
        if (ids.isEmpty()) return PageResponse.of(Page.<com.polygis.audit.AuditEvent>empty(pageable), AuditEventDto::from);
        return PageResponse.of(events.findByActorUserIdIn(ids, Sorts.allow(pageable, "occurredAt", "action")),
                AuditEventDto::from);
    }

    @PostMapping("/team/{userId}/impersonate")
    @PreAuthorize("hasAuthority('org:impersonate')")
    public ImpersonationResponse impersonate(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        Impersonation.reject(jwt); // no nested impersonation
        return new ImpersonationResponse(team.impersonate(OrgAccess.requireUserId(), userId));
    }

    /** Who signed in as me (super-admins and supervisors), newest first. */
    @GetMapping("/access-log")
    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> accessLog(@PageableDefault(size = 10, sort = "occurredAt",
            direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(events.findByTargetTypeAndTargetIdAndActionIn("user",
                OrgAccess.requireUserId().toString(),
                List.of("org.impersonation.started", "user.impersonation_started"),
                Sorts.allow(pageable, "occurredAt")), AuditEventDto::from);
    }
}
