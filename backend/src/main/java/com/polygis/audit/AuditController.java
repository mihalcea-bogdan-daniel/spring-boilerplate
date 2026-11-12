package com.polygis.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.polygis.common.PageResponse;
import com.polygis.common.Sorts;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    public record AuditEventDto(UUID id, Instant occurredAt, String actorEmail, String actorTenant, String action,
                                String targetType, String targetId, String details, String ipAddress) {
        static AuditEventDto from(AuditEvent e) {
            return new AuditEventDto(e.getId(), e.getOccurredAt(), e.getActorEmail(), e.getActorTenant(),
                    e.getAction(), e.getTargetType(), e.getTargetId(), e.getDetails(), e.getIpAddress());
        }
    }

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> list(@RequestParam(required = false) String q,
                                            @PageableDefault(size = 25, sort = "occurredAt",
                                                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(repository.search(q == null ? "" : q.trim(),
                Sorts.allow(pageable, "occurredAt", "action", "actorEmail")), AuditEventDto::from);
    }
}
