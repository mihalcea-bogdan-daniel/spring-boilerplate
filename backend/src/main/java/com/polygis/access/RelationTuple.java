package com.polygis.access;

/** "resource has relation with subject", e.g. {@code project:7f3 owner user:12}. */
public record RelationTuple(ResourceRef resource, String relation, SubjectRef subject) {
}
