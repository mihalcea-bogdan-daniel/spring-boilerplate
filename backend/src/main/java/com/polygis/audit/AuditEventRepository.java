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
}
