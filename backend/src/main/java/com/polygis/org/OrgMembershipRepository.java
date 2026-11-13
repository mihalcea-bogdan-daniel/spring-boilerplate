package com.polygis.org;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgMembershipRepository extends JpaRepository<OrgMembership, OrgMembership.Key> {

    List<OrgMembership> findByUserId(UUID userId);

    boolean existsByUnitId(UUID unitId);

    boolean existsByRankId(UUID rankId);

    /**
     * People the given user supervises: members of a unit where the user holds a supervising rank, or of any unit
     * below it, whose rank level there is lower than the user's level in the leading unit. Disabled and deleted
     * people neither supervise nor are supervised.
     */
    @Query(value = """
            with recursive lead(unit_id, level) as (
                select m.unit_id, r.level
                from org_memberships m
                join org_ranks r on r.id = m.rank_id
                join users u on u.id = m.user_id
                where m.user_id = :userId and r.supervises
                  and u.status = 'ACTIVE' and u.deleted_at is null
            ), scope(unit_id, level) as (
                select unit_id, level from lead
                union
                select c.id, s.level from org_units c join scope s on c.parent_id = s.unit_id
            )
            select distinct m.user_id
            from org_memberships m
            join org_ranks r on r.id = m.rank_id
            join scope s on s.unit_id = m.unit_id
            join users u on u.id = m.user_id
            where r.level < s.level and m.user_id <> :userId
              and u.status <> 'DISABLED' and u.deleted_at is null
            """, nativeQuery = true)
    List<UUID> subordinateIds(@Param("userId") UUID userId);

    /**
     * The highest level with which the user leads the given unit: the best supervising rank the user holds in the
     * unit itself or in any unit above it. Null when the user does not lead it.
     */
    @Query(value = """
            with recursive up(id, parent_id) as (
                select id, parent_id from org_units where id = :unitId
                union
                select p.id, p.parent_id from org_units p join up u on p.id = u.parent_id
            )
            select max(r.level)
            from org_memberships m
            join org_ranks r on r.id = m.rank_id
            join up on up.id = m.unit_id
            join users u on u.id = m.user_id
            where m.user_id = :userId and r.supervises and u.status = 'ACTIVE' and u.deleted_at is null
            """, nativeQuery = true)
    Integer leadLevel(@Param("userId") UUID userId, @Param("unitId") UUID unitId);

    @Query(value = """
            select exists (
                select 1 from org_memberships m
                join org_ranks r on r.id = m.rank_id
                join users u on u.id = m.user_id
                where m.user_id = :userId and r.supervises and u.status = 'ACTIVE' and u.deleted_at is null)
            """, nativeQuery = true)
    boolean leadsAny(@Param("userId") UUID userId);
}
