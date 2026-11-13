package com.polygis.group;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A flat set of people that can own things together: a household, a committee, a project team. */
@Entity
@Table(name = "user_groups")
@Getter
@Setter
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String kind = "team";

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
