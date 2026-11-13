package com.polygis.org;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A person holds one rank in a unit. People may belong to several units. */
@Entity
@Table(name = "org_memberships")
@IdClass(OrgMembership.Key.class)
@Getter
@Setter
public class OrgMembership {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "rank_id", nullable = false)
    private UUID rankId;

    @Column(nullable = false)
    private Instant since = Instant.now();

    @NoArgsConstructor
    @EqualsAndHashCode
    @Getter
    @Setter
    public static class Key implements Serializable {
        private UUID userId;
        private UUID unitId;

        public Key(UUID userId, UUID unitId) {
            this.userId = userId;
            this.unitId = unitId;
        }
    }
}
