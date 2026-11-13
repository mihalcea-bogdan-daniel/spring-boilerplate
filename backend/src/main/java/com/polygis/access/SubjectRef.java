package com.polygis.access;

import java.util.UUID;

/** A user, unit or group. Written {@code user:<id>}, {@code unit:<id>}, {@code group:<id>} in external systems. */
public record SubjectRef(SubjectKind kind, UUID id) {

    public static SubjectRef user(UUID id) {
        return new SubjectRef(SubjectKind.USER, id);
    }

    public static SubjectRef unit(UUID id) {
        return new SubjectRef(SubjectKind.UNIT, id);
    }

    public static SubjectRef group(UUID id) {
        return new SubjectRef(SubjectKind.GROUP, id);
    }

    /** {@code unit:3f2…} - the form used as a candidate-group id in Flowable and similar systems. */
    @Override
    public String toString() {
        return kind.name().toLowerCase() + ":" + id;
    }

    public static SubjectRef parse(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) throw new IllegalArgumentException("Expected <kind>:<uuid>, got " + value);
        return new SubjectRef(SubjectKind.valueOf(value.substring(0, colon).toUpperCase()),
                UUID.fromString(value.substring(colon + 1)));
    }
}
