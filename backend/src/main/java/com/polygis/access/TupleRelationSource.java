package com.polygis.access;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

/** Relations stored in {@code resource_relations}: the default for every type no other source claims. */
@Component
public class TupleRelationSource implements RelationSource {

    /** Stands in for an empty IN list, which PostgreSQL rejects. */
    private static final Set<UUID> NONE = Set.of(new UUID(0, 0));

    private final ResourceRelationRepository repository;

    public TupleRelationSource(ResourceRelationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean supports(String resourceType) {
        return true;
    }

    @Override
    public List<RelationTuple> relationsOf(ResourceRef resource) {
        return repository.findByResourceTypeAndResourceId(resource.type(), resource.id()).stream()
                .map(ResourceRelation::toTuple).toList();
    }

    @Override
    public Optional<List<String>> visibleIds(String resourceType, AccessFilter filter) {
        if (filter.isEmpty()) return Optional.of(List.of());
        return Optional.of(repository.visibleIds(resourceType, filter.relations(), orNone(filter.userIds()),
                orNone(filter.unitIds()), orNone(filter.groupIds())));
    }

    @Override
    public boolean writable() {
        return true;
    }

    @Override
    public void add(RelationTuple t, UUID createdBy) {
        if (repository.findByResourceTypeAndResourceIdAndRelationAndSubjectKindAndSubjectId(t.resource().type(),
                t.resource().id(), t.relation(), t.subject().kind(), t.subject().id()).isPresent()) return;
        ResourceRelation r = new ResourceRelation();
        r.setResourceType(t.resource().type());
        r.setResourceId(t.resource().id());
        r.setRelation(t.relation());
        r.setSubjectKind(t.subject().kind());
        r.setSubjectId(t.subject().id());
        r.setCreatedBy(createdBy);
        repository.save(r);
    }

    @Override
    public void remove(RelationTuple t) {
        repository.findByResourceTypeAndResourceIdAndRelationAndSubjectKindAndSubjectId(t.resource().type(),
                t.resource().id(), t.relation(), t.subject().kind(), t.subject().id()).ifPresent(repository::delete);
    }

    public void removeAll(ResourceRef resource) {
        repository.deleteResource(resource.type(), resource.id());
    }

    private static Set<UUID> orNone(Set<UUID> ids) {
        return ids.isEmpty() ? NONE : ids;
    }
}
