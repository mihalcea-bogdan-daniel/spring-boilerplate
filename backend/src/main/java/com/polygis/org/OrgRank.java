package com.polygis.org;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * A numbered function people hold inside a unit (Director 900, Chief 100, Member 10...). Tenants edit the
 * catalogue: the number decides who outranks whom, the labels and aliases are only names.
 */
@Entity
@Table(name = "org_ranks")
@Getter
@Setter
public class OrgRank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String key;

    @Column(nullable = false)
    private int level;

    /** Display name per locale code, e.g. {"en": "Chief", "ro": "Șef"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> labels = new HashMap<>();

    /** Synonyms used by search (Team lead, Manager...). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] aliases = new String[0];

    /** Whether holders supervise lower-ranked people in their unit and below. */
    @Column(nullable = false)
    private boolean supervises;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
