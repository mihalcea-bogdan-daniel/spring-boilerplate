package com.polygis.tenancy;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByDomain(String domain);

    @Query("""
            select t from Tenant t
            where :q is null or lower(t.slug) like lower(concat('%', :q, '%'))
                             or lower(t.name) like lower(concat('%', :q, '%'))
            """)
    Page<Tenant> search(@Param("q") String q, Pageable pageable);
}
