package com.polygis.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    @Query("select u from OrgUnit u where u.parentId is null")
    Optional<OrgUnit> findRoot();

    List<OrgUnit> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByParentId(UUID parentId);

    /** The unit itself and every unit below it. */
    @Query(value = """
            with recursive sub(id) as (
                select id from org_units where id = :unitId
                union
                select c.id from org_units c join sub s on c.parent_id = s.id
            )
            select id from sub
            """, nativeQuery = true)
    List<UUID> subtreeIds(@Param("unitId") UUID unitId);

    /** The unit itself and every unit above it, up to the root. */
    @Query(value = """
            with recursive up(id, parent_id) as (
                select id, parent_id from org_units where id = :unitId
                union
                select p.id, p.parent_id from org_units p join up u on p.id = u.parent_id
            )
            select id from up
            """, nativeQuery = true)
    List<UUID> ancestorIds(@Param("unitId") UUID unitId);
}
