package com.polygis.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", length = 320)
    private String actorEmail;

    @Column(name = "actor_tenant", length = 42)
    private String actorTenant;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 100)
    private String targetId;

    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;
}
