package com.polygis.access;

/**
 * Anything that can be owned: a row in a module's table, a file in object storage, a task in a workflow engine.
 * The id is a string so external ids fit.
 */
public record ResourceRef(String type, String id) {

    public static ResourceRef of(String type, Object id) {
        return new ResourceRef(type, String.valueOf(id));
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
