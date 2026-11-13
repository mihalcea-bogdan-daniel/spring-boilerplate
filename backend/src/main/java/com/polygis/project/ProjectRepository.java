package com.polygis.project;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("""
            select p from Project p
            where (:all = true or p.id in :ids)
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Project> search(@Param("all") boolean all, @Param("ids") Collection<UUID> ids, @Param("q") String q,
                         Pageable pageable);
}
