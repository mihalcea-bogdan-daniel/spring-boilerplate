package com.polygis.access;

import java.util.Set;
import java.util.UUID;

/**
 * What a caller can see for one action on one resource type: relations naming one of these users, units or
 * groups. {@code everything} means an admin permission bypasses relations. Modules and external sources turn it
 * into their own query (SQL subquery, Flowable {@code taskCandidateGroupIn(...)}, ...).
 */
public record AccessFilter(boolean everything, Set<String> relations, Set<UUID> userIds, Set<UUID> unitIds,
                           Set<UUID> groupIds) {

    public static AccessFilter all() {
        return new AccessFilter(true, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static AccessFilter nothing() {
        return new AccessFilter(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public boolean matches(RelationTuple t) {
        if (everything) return true;
        if (!relations.contains(t.relation())) return false;
        UUID id = t.subject().id();
        return switch (t.subject().kind()) {
            case USER -> userIds.contains(id);
            case UNIT -> unitIds.contains(id);
            case GROUP -> groupIds.contains(id);
        };
    }

    public boolean isEmpty() {
        return !everything && (relations.isEmpty() || (userIds.isEmpty() && unitIds.isEmpty() && groupIds.isEmpty()));
    }

    /** The subjects in the {@code kind:id} form external systems store, e.g. as Flowable candidate groups. */
    public Set<String> subjectKeys() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        userIds.forEach(id -> keys.add(SubjectRef.user(id).toString()));
        unitIds.forEach(id -> keys.add(SubjectRef.unit(id).toString()));
        groupIds.forEach(id -> keys.add(SubjectRef.group(id).toString()));
        return keys;
    }
}
