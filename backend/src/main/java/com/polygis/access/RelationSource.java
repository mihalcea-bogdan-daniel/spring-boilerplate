package com.polygis.access;

import java.util.List;
import java.util.Optional;

/**
 * Where the relations of some resource types live. The default {@link TupleRelationSource} keeps them in the
 * {@code resource_relations} table. Systems that track their own assignments plug in here instead of copying
 * them, e.g. a Flowable adapter that maps a task's assignee to {@code user:<id>} and its candidate groups
 * ({@code unit:<id>}, {@code group:<id>}) to subjects.
 */
public interface RelationSource {

    /** Types this source answers for. The tuple store answers for every type no other source claims. */
    boolean supports(String resourceType);

    List<RelationTuple> relationsOf(ResourceRef resource);

    /**
     * Ids of the resources of a type the filter can see. Sources that can't list (or list better in their own
     * query language) return empty and the module queries them with {@link AccessFilter} itself.
     */
    Optional<List<String>> visibleIds(String resourceType, AccessFilter filter);

    /** Whether relations can be granted and revoked through the access API. External sources usually can't. */
    default boolean writable() {
        return false;
    }

    default void add(RelationTuple tuple, java.util.UUID createdBy) {
        throw new UnsupportedOperationException("Relations of this type are managed by their own system");
    }

    default void remove(RelationTuple tuple) {
        throw new UnsupportedOperationException("Relations of this type are managed by their own system");
    }
}
