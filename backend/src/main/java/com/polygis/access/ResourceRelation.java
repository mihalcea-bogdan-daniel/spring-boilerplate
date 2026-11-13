package com.polygis.access;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "resource_relations")
@Getter
@Setter
public class ResourceRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "resource_type", nullable = false, length = 60)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 200)
    private String resourceId;

    @Column(nullable = false, length = 40)
    private String relation;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_kind", nullable = false, length = 10)
    private SubjectKind subjectKind;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RelationTuple toTuple() {
        return new RelationTuple(new ResourceRef(resourceType, resourceId), relation,
                new SubjectRef(subjectKind, subjectId));
    }
}
