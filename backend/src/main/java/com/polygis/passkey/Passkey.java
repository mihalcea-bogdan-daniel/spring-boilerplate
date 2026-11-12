package com.polygis.passkey;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A WebAuthn credential (passkey) bound to the tenant host it was created on. */
@Entity
@Table(name = "passkeys")
@Getter
@Setter
public class Passkey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1400)
    private String credentialId;

    @Column(name = "credential_data", nullable = false)
    private byte[] credentialData;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(name = "uv_initialized", nullable = false)
    private boolean uvInitialized;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @Column(length = 200)
    private String transports;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
