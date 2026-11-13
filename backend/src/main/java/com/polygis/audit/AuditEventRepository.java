package com.polygis.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            select e from AuditEvent e
            where :q = ''
               or lower(e.action) like lower(concat('%', :q, '%'))
               or lower(coalesce(e.actorEmail, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(e.targetId, '')) like lower(concat('%', :q, '%'))
            """)
    Page<AuditEvent> search(@Param("q") String q, Pageable pageable);

    /** Activity of a set of people, e.g. the people a supervisor is responsible for. */
    Page<AuditEvent> findByActorUserIdIn(java.util.Collection<UUID> actorUserIds, Pageable pageable);

    /** Events about one target with one of the given actions, e.g. who signed in as a user. */
    Page<AuditEvent> findByTargetTypeAndTargetIdAndActionIn(String targetType, String targetId,
                                                            java.util.Collection<String> actions, Pageable pageable);
}
