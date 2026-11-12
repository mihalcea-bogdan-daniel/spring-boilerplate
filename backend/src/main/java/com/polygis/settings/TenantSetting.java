package com.polygis.settings;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
public class TenantSetting {

    @Id
    @Column(length = 100)
    private String key;

    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    @PrePersist
    void touch() {
        updatedAt = Instant.now();
    }
}
