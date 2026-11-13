package com.polygis.access;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRelationRepository extends JpaRepository<ResourceRelation, UUID> {

    List<ResourceRelation> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    Optional<ResourceRelation> findByResourceTypeAndResourceIdAndRelationAndSubjectKindAndSubjectId(
            String resourceType, String resourceId, String relation, SubjectKind subjectKind, UUID subjectId);

    @Modifying
    @Query("delete from ResourceRelation r where r.resourceType = :type and r.resourceId = :id")
    void deleteResource(@Param("type") String type, @Param("id") String id);

    /** Ids of resources of a type that name one of the subjects with one of the relations. Lists must not be empty. */
    @Query(value = """
            select distinct resource_id from resource_relations
            where resource_type = :type and relation in (:relations)
              and ((subject_kind = 'USER'  and subject_id in (:users))
                or (subject_kind = 'UNIT'  and subject_id in (:units))
                or (subject_kind = 'GROUP' and subject_id in (:groups)))
            """, nativeQuery = true)
    List<String> visibleIds(@Param("type") String type, @Param("relations") Collection<String> relations,
                            @Param("users") Collection<UUID> users, @Param("units") Collection<UUID> units,
                            @Param("groups") Collection<UUID> groups);
}
