package com.polygis.access;

/** Who a relation can point to. */
public enum SubjectKind {
    /** One person. */
    USER,
    /** An org unit: its members, and (per resource type) members of the units below it. */
    UNIT,
    /** A flat group such as a household: its members. */
    GROUP
}
